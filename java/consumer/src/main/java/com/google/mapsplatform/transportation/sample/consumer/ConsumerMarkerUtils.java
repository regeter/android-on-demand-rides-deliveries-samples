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
package com.google.mapsplatform.transportation.sample.consumer;

import static com.google.mapsplatform.transportation.sample.consumer.ConsumerMarkerType.DROPOFF_POINT;
import static com.google.mapsplatform.transportation.sample.consumer.ConsumerMarkerType.INTERMEDIATE_DESTINATION_POINT;
import static com.google.mapsplatform.transportation.sample.consumer.ConsumerMarkerType.PICKUP_POINT;
import static com.google.mapsplatform.transportation.sample.consumer.ConsumerMarkerType.PREVIOUS_TRIP_PENDING_POINT;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.Color;
import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.mapsplatform.transportation.consumer.model.MarkerType;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerController;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerMapStyle;

/** Marker util class to get the correct image based on the marker types. */
public final class ConsumerMarkerUtils {

  /** Anchor value for the markers to display on the map. */
  private static final float ANCHOR_VALUE = 0.5f;

  /**
   * Baseed on ConsumerMarkerType, get the MarkerOptions to display on the map with the
   * corresponding images.
   */
  public static MarkerOptions getConsumerMarkerOptions(
      Context context, @ConsumerMarkerType int consumerMarkerType) {
    switch (consumerMarkerType) {
      case PICKUP_POINT:
        return new MarkerOptions()
            .anchor(ANCHOR_VALUE, ANCHOR_VALUE)
            .icon(toBitmapDescriptor(context, R.drawable.ic_custom_pickup));
      case DROPOFF_POINT:
        return new MarkerOptions()
            .anchor(ANCHOR_VALUE, ANCHOR_VALUE)
            .icon(toBitmapDescriptor(context, R.drawable.ic_custom_dropoff));
      case INTERMEDIATE_DESTINATION_POINT:
        return new MarkerOptions()
            .anchor(ANCHOR_VALUE, ANCHOR_VALUE)
            .icon(toBitmapDescriptor(context, R.drawable.ic_intermediate_waypoint));
      case PREVIOUS_TRIP_PENDING_POINT:
        return new MarkerOptions()
            .anchor(ANCHOR_VALUE, ANCHOR_VALUE)
            .icon(toBitmapDescriptor(context, R.drawable.ic_intermediate_destination));
      default:
        throw new IllegalArgumentException(
            String.format("Marker type: %d not supported", consumerMarkerType));
    }
  }

  /** Returns marker options to display the vehicle marker in Picture-in-picture mode. */
  public static MarkerOptions getShrunkVehicleMarkerOptions(Context context) {
    Bitmap bitmap = getBitmapForMarker(context, R.drawable.ic_custom_trip_vehicle);

    Bitmap scaledBitmap =
            Bitmap.createScaledBitmap(
                    bitmap, 80, 80, /* filter= */ false);

    return new MarkerOptions()
            .flat(false)
            .icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap));
  }

  /** Convert a vector drawable to a bitmap descriptor. */
  public static BitmapDescriptor toBitmapDescriptor(Context context, @DrawableRes int resourceId) {
    Drawable vectorDrawable = AppCompatResources.getDrawable(context, resourceId);
    Bitmap bitmap =
        Bitmap.createBitmap(
            vectorDrawable.getIntrinsicWidth(),
            vectorDrawable.getIntrinsicHeight(),
            Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    vectorDrawable.draw(canvas);
    return BitmapDescriptorFactory.fromBitmap(bitmap);
  }

  /** Customize the pickup and drop-off markers. */
  public static void setCustomMarkers(ConsumerController mConsumerController, Context context) {
    ConsumerMapStyle mapStyle = mConsumerController.getConsumerMapStyle();

    MarkerOptions pickupMarkerStyleOptions =
        new MarkerOptions().icon(toBitmapDescriptor(context, R.drawable.ic_custommarker_pickup));
    mapStyle.setMarkerStyleOptions(MarkerType.TRIP_PICKUP_POINT, pickupMarkerStyleOptions);

    MarkerOptions dropoffMarkerStyleOptions =
        new MarkerOptions()
            .icon(toBitmapDescriptor(context, R.drawable.ic_custommarker_destination));
    mapStyle.setMarkerStyleOptions(MarkerType.TRIP_DROPOFF_POINT, dropoffMarkerStyleOptions);

    MarkerOptions intermediateMarkerStyleOptions =
        new MarkerOptions().icon(toBitmapDescriptor(context, R.drawable.ic_intermediate_waypoint));
    mapStyle.setMarkerStyleOptions(
        MarkerType.TRIP_INTERMEDIATE_DESTINATION, intermediateMarkerStyleOptions);
  }

  private static Bitmap getBitmapForMarker(Context context, int resId) {
    Drawable vectorDrawable = AppCompatResources.getDrawable(context, resId);
    Bitmap bitmap =
            Bitmap.createBitmap(
                    vectorDrawable.getIntrinsicWidth(),
                    vectorDrawable.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    vectorDrawable.draw(canvas);

    return textAsBitmap("Test", 120,1);
  }

  public static Bitmap textAsBitmap(String text, float textSize, int textColor) {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setTextSize(textSize);
    paint.setColor(Color.BLACK);
    paint.setTextAlign(Paint.Align.LEFT);
    float baseline = -paint.ascent(); // ascent() is negative
    int width = (int) (paint.measureText(text) + 0.0f); // round
    int height = (int) (baseline + paint.descent() + 0.0f);

    int trueWidth = width;
    if(width>height)height=width; else width=height;
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

    Canvas canvas = new Canvas(image);
    canvas.drawText(text, width/2-trueWidth/2, baseline, paint);
    return image;
  }

}
