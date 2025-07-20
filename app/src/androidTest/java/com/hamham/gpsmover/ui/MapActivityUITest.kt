package com.hamham.gpsmover.view

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hamham.gpsmover.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapActivityUITest {

    @Test
    fun testMapActivityLaunches() {
        ActivityScenario.launch(MapActivity::class.java)
        
        // Check if map container is displayed
        onView(withId(R.id.map_container))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testBottomNavigationExists() {
        ActivityScenario.launch(MapActivity::class.java)
        
        // Check if bottom navigation is displayed
        onView(withId(R.id.bottom_navigation))
            .check(matches(isDisplayed()))
        
        // Check if all navigation items exist
        onView(withId(R.id.navigation_map))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.navigation_favorites))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.navigation_settings))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testNavigationToFavorites() {
        ActivityScenario.launch(MapActivity::class.java)
        
        // Click on favorites navigation item
        onView(withId(R.id.navigation_favorites))
            .perform(click())
        
        // Check if favorites page is displayed
        onView(withId(R.id.favorites_page))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testNavigationToSettings() {
        ActivityScenario.launch(MapActivity::class.java)
        
        // Click on settings navigation item
        onView(withId(R.id.navigation_settings))
            .perform(click())
        
        // Check if settings page is displayed
        onView(withId(R.id.settings_page))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testNavigationBackToMap() {
        ActivityScenario.launch(MapActivity::class.java)
        
        // Navigate to favorites first
        onView(withId(R.id.navigation_favorites))
            .perform(click())
        
        // Navigate back to map
        onView(withId(R.id.navigation_map))
            .perform(click())
        
        // Check if map container is displayed
        onView(withId(R.id.map_container))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testFloatingActionButtonsExist() {
        ActivityScenario.launch(MapActivity::class.java)
        
        // Check if FABs exist
        onView(withId(R.id.add_fav_fab))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.my_location_fab))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testSearchBarExists() {
        ActivityScenario.launch(MapActivity::class.java)
        
        // Check if search bar exists
        onView(withId(R.id.search_edit_text))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.search_send_button))
            .check(matches(isDisplayed()))
    }
} 