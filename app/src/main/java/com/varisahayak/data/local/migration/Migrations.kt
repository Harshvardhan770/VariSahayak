package com.varisahayak.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations.
 *
 * `fallbackToDestructiveMigration` is deliberately never set on this database. Wiping it
 * on a schema change would discard incidents and Lost & Found reports that have not
 * reached the server yet — the one failure this product cannot accept. Every version bump
 * therefore needs a real migration, and a missing one fails loudly in development instead
 * of quietly in the field.
 */

/**
 * 1 → 2: Plan 07.
 *
 * Reshapes `lost_found_items` from the original single-sided model into the Lost/Found
 * pair, and adds custody, match and cached-location tables.
 *
 * SQLite cannot drop or retype a column, so the table is rebuilt and copied. The copy is
 * the important part: any report a volunteer filed before this upgrade — including one
 * still queued for sync — survives it.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lost_found_items_new` (
                `clientId` TEXT NOT NULL,
                `serverId` TEXT,
                `incidentClientId` TEXT,
                `kind` TEXT NOT NULL,
                `subjectType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `personName` TEXT,
                `approximateAge` INTEGER,
                `gender` TEXT,
                `approximateHeightCm` INTEGER,
                `clothingDescription` TEXT,
                `physicalDescription` TEXT,
                `language` TEXT,
                `condition` TEXT,
                `additionalNotes` TEXT,
                `guardianName` TEXT,
                `guardianPhone` TEXT,
                `qrLocationToken` TEXT,
                `qrLocationName` TEXT,
                `deviceLatitude` REAL,
                `deviceLongitude` REAL,
                `lastKnownLatitude` REAL,
                `lastKnownLongitude` REAL,
                `routeSegment` TEXT,
                `routeSequence` INTEGER,
                `occurredAtEpochMillis` INTEGER,
                `reportedAtEpochMillis` INTEGER NOT NULL,
                `photoLocalPath` TEXT,
                `photoRemotePath` TEXT,
                `faceMatchStatus` TEXT NOT NULL,
                `custodianUserId` TEXT,
                `custodianName` TEXT,
                `custodianContact` TEXT,
                `status` TEXT NOT NULL,
                `reportedBy` TEXT NOT NULL,
                `syncState` TEXT NOT NULL,
                PRIMARY KEY(`clientId`)
            )
            """.trimIndent(),
        )

        // Carry the old rows across.
        //
        // The old `kind` column held PERSON/ITEM — that is the *subject*, not the side of
        // the separation. Every pre-Plan-07 report described something that had been lost,
        // so all of them become kind = LOST with their old value as subjectType.
        //
        // `lastSeen*` becomes `lastKnown*`, and the old RESOLVED status becomes REUNITED.
        db.execSQL(
            """
            INSERT INTO `lost_found_items_new` (
                clientId, serverId, incidentClientId, kind, subjectType, title, description,
                qrLocationToken, lastKnownLatitude, lastKnownLongitude, occurredAtEpochMillis,
                reportedAtEpochMillis, photoLocalPath, faceMatchStatus, status, reportedBy,
                syncState
            )
            SELECT
                clientId,
                serverId,
                incidentClientId,
                'LOST',
                CASE WHEN kind = 'ITEM' THEN 'ITEM' ELSE 'PERSON' END,
                title,
                description,
                qrToken,
                lastSeenLatitude,
                lastSeenLongitude,
                lastSeenAtEpochMillis,
                reportedAtEpochMillis,
                photoLocalPath,
                CASE WHEN photoLocalPath IS NULL THEN 'NOT_APPLICABLE' ELSE 'PENDING' END,
                CASE WHEN status = 'RESOLVED' THEN 'REUNITED' ELSE status END,
                reportedBy,
                syncState
            FROM `lost_found_items`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `lost_found_items`")
        db.execSQL("ALTER TABLE `lost_found_items_new` RENAME TO `lost_found_items`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_lost_found_items_status` " +
                "ON `lost_found_items` (`status`)",
        )

        // --- custody chain ---------------------------------------------------------------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lost_found_custody` (
                `clientId` TEXT NOT NULL,
                `reportClientId` TEXT NOT NULL,
                `custodianUserId` TEXT NOT NULL,
                `custodianName` TEXT,
                `helpPointName` TEXT,
                `qrLocationToken` TEXT,
                `latitude` REAL,
                `longitude` REAL,
                `fromEpochMillis` INTEGER NOT NULL,
                `untilEpochMillis` INTEGER,
                `handoverNote` TEXT,
                `syncState` TEXT NOT NULL,
                PRIMARY KEY(`clientId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_lost_found_custody_reportClientId` " +
                "ON `lost_found_custody` (`reportClientId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_lost_found_custody_custodianUserId` " +
                "ON `lost_found_custody` (`custodianUserId`)",
        )

        // --- candidate matches -----------------------------------------------------------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lost_found_matches` (
                `clientId` TEXT NOT NULL,
                `serverId` TEXT,
                `lostReportClientId` TEXT NOT NULL,
                `foundReportClientId` TEXT NOT NULL,
                `overallScore` REAL NOT NULL,
                `confidence` TEXT NOT NULL,
                `signalsJson` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `reviewedBy` TEXT,
                `reviewedAtEpochMillis` INTEGER,
                `reviewNote` TEXT,
                `syncState` TEXT NOT NULL,
                PRIMARY KEY(`clientId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_lost_found_matches_lostReportClientId` " +
                "ON `lost_found_matches` (`lostReportClientId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_lost_found_matches_foundReportClientId` " +
                "ON `lost_found_matches` (`foundReportClientId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_lost_found_matches_status` " +
                "ON `lost_found_matches` (`status`)",
        )
        // Unique on the pair: the engine runs repeatedly, and without this a candidate
        // would be re-inserted — and re-notified — on every pass.
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_lost_found_matches_lostReportClientId_foundReportClientId` " +
                "ON `lost_found_matches` (`lostReportClientId`, `foundReportClientId`)",
        )

        // --- cached QR locations ---------------------------------------------------------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `qr_locations` (
                `token` TEXT NOT NULL,
                `locationName` TEXT NOT NULL,
                `description` TEXT,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `routeSegment` TEXT,
                `routeSequence` INTEGER,
                `locationType` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `publicPageEnabled` INTEGER NOT NULL,
                `areaId` TEXT,
                `organisationId` TEXT,
                `lastVerifiedAtEpochMillis` INTEGER,
                `cachedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`token`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_qr_locations_routeSequence` " +
                "ON `qr_locations` (`routeSequence`)",
        )
    }
}

/** Every migration, in order. Registered in DatabaseModule. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
