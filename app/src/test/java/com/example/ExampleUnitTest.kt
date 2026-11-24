package com.example

import com.example.data.repository.LocationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun parseCoordinateInput_acceptsValidFormats() {
    val commaSeparated = LocationRepository.parseCoordinateInput("37.7955, -122.3937")
    assertNotNull(commaSeparated)
    assertEquals(37.7955, commaSeparated!!.first, 0.00001)
    assertEquals(-122.3937, commaSeparated.second, 0.00001)

    val spaceSeparated = LocationRepository.parseCoordinateInput("51.5007 -0.1246")
    assertNotNull(spaceSeparated)
    assertEquals(51.5007, spaceSeparated!!.first, 0.00001)
    assertEquals(-0.1246, spaceSeparated.second, 0.00001)
  }

  @Test
  fun parseCoordinateInput_rejectsOutOfRangeOrMalformed() {
    assertNull(LocationRepository.parseCoordinateInput("95.0, 10.0"))
    assertNull(LocationRepository.parseCoordinateInput("10.0, 185.0"))
    assertNull(LocationRepository.parseCoordinateInput("San Francisco"))
  }

  @Test
  fun parseCoordinateInput_acceptsLinksAndHemisphereSuffixes() {
    val mapsUrl = LocationRepository.parseCoordinateInput("https://maps.google.com/?q=28.613939,77.209021")
    assertNotNull(mapsUrl)
    assertEquals(28.613939, mapsUrl!!.first, 0.00001)
    assertEquals(77.209021, mapsUrl.second, 0.00001)

    val geoUri = LocationRepository.parseCoordinateInput("geo:35.6595,139.7004")
    assertNotNull(geoUri)
    assertEquals(35.6595, geoUri!!.first, 0.00001)
    assertEquals(139.7004, geoUri.second, 0.00001)

    val hemi = LocationRepository.parseCoordinateInput("33.8688° S, 151.2093° E")
    assertNotNull(hemi)
    assertEquals(-33.8688, hemi!!.first, 0.00001)
    assertEquals(151.2093, hemi.second, 0.00001)
  }

  @Test
  fun distanceKm_andMeters_computesReasonableGeodesicDistance() {
    val km = LocationRepository.distanceKm(37.7955, -122.3937, 40.7580, -73.9855)
    assertTrue("Expected ~4129 km, got $km", km in 4050.0..4200.0)

    val meters = LocationRepository.distanceMeters(28.6139, 77.2090, 28.6139, 77.2090)
    assertEquals(0.0, meters, 0.01)
  }
}
