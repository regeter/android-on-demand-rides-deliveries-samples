/* Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mapsplatform.transportation.sample.kotlinconsumer.provider.model

import com.google.android.gms.maps.model.LatLng
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * Java object for search trips for sample provider.
 * https://developers.google.com/maps/documentation/mobility/fleet-engine/reference/trips/rest/v1/providers.trips/search
 */
class SearchTripsRequest(
    @SerializedName("vehicleId") @Expose val vehicleId: String? = null,
    @SerializedName("activeTripsOnly") @Expose val activeTripsOnly: Boolean? = null,
    @SerializedName("pageSize") @Expose val pageSize: Int? = null,
    @SerializedName("pageToken") @Expose val pageToken: String? = null,
    @SerializedName("minimumStaleness") @Expose val minimumStaleness: String? = null
)
