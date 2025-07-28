package com.google.mapsplatform.transportation.sample.kotlinconsumer.provider.response

import com.google.gson.annotations.SerializedName

class SearchTripsResponse {
    @SerializedName("trips") val trips: List<TripResponse>  = listOf()
    @SerializedName("nextPageToken") val nextPageToken: String? = null
}