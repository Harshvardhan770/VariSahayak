package com.varisahayak.domain.usecase

import android.content.Context
import com.varisahayak.R
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AlertMessage(
    val title: String,
    val body: String,
    val locationLabel: String,
    val actionRequired: String? = null
)

@Singleton
class AlertTemplateGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun generate(incident: Incident, nowMillis: Long): AlertMessage {
        val category = incident.category
        val isSos = incident.isSos
        
        val locationStr = incident.areaId ?: context.getString(R.string.alert_approx_location)
        val timeStr = com.varisahayak.core.utils.DateTimeUtils.formatRelativeTime(
            context,
            incident.reportedAtEpochMillis,
            nowMillis
        )
        
        return if (isSos) {
            generateSosMessage(incident, locationStr, timeStr)
        } else {
            generateCategoryMessage(category, incident, locationStr, timeStr)
        }
    }

    private fun generateSosMessage(incident: Incident, location: String, time: String): AlertMessage {
        val categoryLabel = context.getString(incident.category.labelRes())
        return AlertMessage(
            title = context.getString(R.string.alert_tpl_sos_critical_title),
            body = context.getString(
                R.string.alert_tpl_sos_critical_body,
                location,
                categoryLabel,
                time
            ),
            locationLabel = location
        )
    }

    private fun generateCategoryMessage(
        category: IncidentCategory,
        incident: Incident,
        location: String,
        time: String
    ): AlertMessage {
        return when (category) {
            IncidentCategory.MEDICAL -> AlertMessage(
                title = context.getString(R.string.alert_tpl_medical_title),
                body = context.getString(
                    R.string.alert_tpl_medical_body,
                    location,
                    time,
                    incident.affectedPersonNote ?: "1"
                ),
                locationLabel = location
            )
            IncidentCategory.LOST_PERSON -> {
                val isChild = (incident.affectedPersonNote?.lowercase()?.contains("child") == true)
                if (isChild) {
                    AlertMessage(
                        title = context.getString(R.string.alert_tpl_missing_child_title),
                        body = context.getString(
                            R.string.alert_tpl_missing_child_body,
                            location,
                            incident.description
                        ),
                        locationLabel = location
                    )
                } else {
                    AlertMessage(
                        title = context.getString(R.string.alert_tpl_lost_person_title),
                        body = context.getString(
                            R.string.alert_tpl_lost_person_body,
                            location,
                            incident.description,
                            location
                        ),
                        locationLabel = location
                    )
                }
            }
            IncidentCategory.CROWD_SURGE -> AlertMessage(
                title = context.getString(R.string.alert_tpl_crowd_surge_title),
                body = context.getString(
                    R.string.alert_tpl_crowd_surge_body,
                    location,
                    incident.status.name
                ),
                locationLabel = location
            )
            IncidentCategory.BLOCKED_ROAD -> AlertMessage(
                title = context.getString(R.string.alert_tpl_road_blockage_title),
                body = context.getString(
                    R.string.alert_tpl_road_blockage_body,
                    location,
                    incident.description
                ),
                locationLabel = location
            )
            IncidentCategory.WATER -> AlertMessage(
                title = context.getString(R.string.alert_tpl_water_shortage_title),
                body = context.getString(
                    R.string.alert_tpl_water_shortage_body,
                    location
                ),
                locationLabel = location
            )
            IncidentCategory.SANITATION -> AlertMessage(
                title = context.getString(R.string.alert_tpl_sanitation_title),
                body = context.getString(
                    R.string.alert_tpl_sanitation_body,
                    location
                ),
                locationLabel = location
            )
            else -> AlertMessage(
                title = context.getString(R.string.alert_tpl_general_title),
                body = context.getString(
                    R.string.alert_tpl_general_body,
                    location,
                    incident.description
                ),
                locationLabel = location
            )
        }
    }
}

// Extension to map category to its label resource
private fun IncidentCategory.labelRes(): Int = when (this) {
    IncidentCategory.MEDICAL -> R.string.category_medical
    IncidentCategory.WATER -> R.string.category_water
    IncidentCategory.LOST_PERSON -> R.string.category_lost_person
    IncidentCategory.BLOCKED_ROAD -> R.string.category_blocked_road
    IncidentCategory.SANITATION -> R.string.category_sanitation
    IncidentCategory.CROWD_SURGE -> R.string.category_crowd_surge
    IncidentCategory.OTHER -> R.string.category_other
}
