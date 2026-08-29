package com.varisahayak.data.repository

import androidx.compose.ui.graphics.Color
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Palkhi
import com.varisahayak.domain.model.PalkhiStop
import com.varisahayak.domain.repository.PalkhiRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PalkhiRepositoryImpl @Inject constructor() : PalkhiRepository {

    private val _palkhis = MutableStateFlow(INITIAL_PALKHIS)

    override fun observePalkhis(): Flow<List<Palkhi>> = _palkhis.asStateFlow()

    override suspend fun refreshPositions() {
        // In a real app, this would fetch from an API.
        // For the demo, we simulate a slight shift in position if needed,
        // or just keep them at their current stops.
        delay(1000)
    }

    companion object {
        private val TUKARAM_ROUTE = listOf(
            PalkhiStop("Dehu", GeoPoint(18.7188, 73.7691), "July 7"),
            PalkhiStop("Akurdi", GeoPoint(18.6493, 73.7667), "July 7"),
            PalkhiStop("Pune", GeoPoint(18.5204, 73.8567), "July 8-9"),
            PalkhiStop("Loni Kalbhor", GeoPoint(18.4847, 74.0205), "July 10"),
            PalkhiStop("Yavat", GeoPoint(18.4727, 74.2863), "July 11"),
            PalkhiStop("Baramati", GeoPoint(18.1506, 74.5771), "July 15"),
            PalkhiStop("Indapur", GeoPoint(18.1130, 75.0253), "July 18"),
            PalkhiStop("Akluj", GeoPoint(17.8938, 75.0232), "July 20"),
            PalkhiStop("Wakhri", GeoPoint(17.7086, 75.2952), "July 23"),
            PalkhiStop("Pandharpur", GeoPoint(17.6778, 75.3283), "July 24")
        )

        private val DNYANESHWAR_ROUTE = listOf(
            PalkhiStop("Alandi", GeoPoint(18.6756, 73.8906), "July 8"),
            PalkhiStop("Pune", GeoPoint(18.5204, 73.8567), "July 9-10"),
            PalkhiStop("Saswad", GeoPoint(18.3444, 74.0270), "July 11"),
            PalkhiStop("Jejuri", GeoPoint(18.2778, 74.1610), "July 13"),
            PalkhiStop("Lonand", GeoPoint(18.0401, 74.1914), "July 15"),
            PalkhiStop("Phaltan", GeoPoint(17.9839, 74.4367), "July 17"),
            PalkhiStop("Natepute", GeoPoint(17.9157, 74.7701), "July 19"),
            PalkhiStop("Wakhri", GeoPoint(17.7086, 75.2952), "July 23"),
            PalkhiStop("Pandharpur", GeoPoint(17.6778, 75.3283), "July 24")
        )

        private val INITIAL_PALKHIS = listOf(
            Palkhi(
                id = "tukaram",
                name = "Sant Tukaram Maharaj Palkhi",
                color = Color(0xFFFF9800), // Orange/Saffron
                route = TUKARAM_ROUTE,
                currentPosition = TUKARAM_ROUTE[2].location, // Currently in Pune for demo
                lastUpdatedEpochMillis = System.currentTimeMillis()
            ),
            Palkhi(
                id = "dnyaneshwar",
                name = "Sant Dnyaneshwar Maharaj Palkhi",
                color = Color(0xFF2196F3), // Blue
                route = DNYANESHWAR_ROUTE,
                currentPosition = DNYANESHWAR_ROUTE[1].location, // Currently in Pune for demo
                lastUpdatedEpochMillis = System.currentTimeMillis()
            )
        )
    }
}
