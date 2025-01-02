package com.izziani.skytone.network

import retrofit2.http.GET
import retrofit2.http.Query

data class SunriseSunsetResponse(
    val results: Results,
    val status: String
)

data class Results(
    val sunrise: String,
    val sunset: String,
    val civil_twilight_begin: String,
    val civil_twilight_end: String
)

interface SunriseSunsetApi {
    @GET("json")
    suspend fun getSunriseSunsetTimes(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("formatted") formatted: Int = 0
    ): SunriseSunsetResponse
}
