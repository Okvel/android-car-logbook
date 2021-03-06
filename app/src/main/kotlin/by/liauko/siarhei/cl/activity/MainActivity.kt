package by.liauko.siarhei.cl.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import by.liauko.siarhei.cl.R
import by.liauko.siarhei.cl.activity.fragment.DataFragment
import by.liauko.siarhei.cl.activity.fragment.SettingsFragment
import by.liauko.siarhei.cl.database.CarLogbookDatabase
import by.liauko.siarhei.cl.repository.CarProfileRepository
import by.liauko.siarhei.cl.repository.LogRepository
import by.liauko.siarhei.cl.repository.SelectAllCarProfileAsyncTask
import by.liauko.siarhei.cl.util.AppResultCodes.CAR_PROFILE_FIRST_START
import by.liauko.siarhei.cl.util.AppResultCodes.CAR_PROFILE_SHOW_LIST
import by.liauko.siarhei.cl.util.AppResultCodes.LOG_EXPORT
import by.liauko.siarhei.cl.util.AppResultCodes.PERIOD_DIALOG_RESULT
import by.liauko.siarhei.cl.util.ApplicationUtil.EMPTY_STRING
import by.liauko.siarhei.cl.util.ApplicationUtil.createAlertDialog
import by.liauko.siarhei.cl.util.ApplicationUtil.dataPeriod
import by.liauko.siarhei.cl.util.ApplicationUtil.periodCalendar
import by.liauko.siarhei.cl.util.ApplicationUtil.profileId
import by.liauko.siarhei.cl.util.ApplicationUtil.profileName
import by.liauko.siarhei.cl.util.ApplicationUtil.type
import by.liauko.siarhei.cl.util.DataPeriod
import by.liauko.siarhei.cl.util.DataType
import by.liauko.siarhei.cl.util.ExportToPdfAsyncTask
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Calendar

class MainActivity : AppCompatActivity(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val defaultCarProfileName = "Default Car Profile"

    private lateinit var toolbar: Toolbar
    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = getSharedPreferences(getString(R.string.shared_preferences_name), Context.MODE_PRIVATE)
        type = DataType.valueOf(preferences.getString(getString(R.string.main_screen_key), "LOG") ?: "LOG")
        dataPeriod = DataPeriod.valueOf(preferences.getString(getString(R.string.period_key), "MONTH") ?: "MONTH")
        val defaultUiMode = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
        else
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        val uiMode = preferences.getInt(getString(R.string.theme_key), defaultUiMode)
        AppCompatDelegate.setDefaultNightMode(uiMode)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profileId = preferences.getLong(getString(R.string.car_profile_id_key), -1L)
        profileName = preferences.getString(getString(R.string.car_profile_name_key), getString(R.string.app_name)) ?: EMPTY_STRING
        checkCarProfile(preferences)

        initToolbar()
        initBottomNavigationView()

        if (savedInstanceState == null) {
            startActivity(Intent(applicationContext, LaunchScreenActivity::class.java))
        }
    }

    private fun initToolbar() {
        toolbar = findViewById(R.id.toolbar)
        toolbar.inflateMenu(R.menu.period_select_menu)
        toolbar.setOnMenuItemClickListener {
            var result = false
            when (it.itemId) {
                R.id.period_select_menu_date -> {
                    startActivityForResult(Intent(applicationContext, PeriodSelectorActivity::class.java), PERIOD_DIALOG_RESULT)
                    result = true
                }
                R.id.car_profile_menu -> {
                    startActivityForResult(Intent(applicationContext, CarProfilesActivity::class.java), CAR_PROFILE_SHOW_LIST)
                    result = true
                }
                R.id.export_to_pdf -> {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), LOG_EXPORT)
                    result = true
                }
            }

            return@setOnMenuItemClickListener result
        }
    }

    private fun initBottomNavigationView() {
        bottomNavigationView = findViewById(R.id.bottom_navigation_view)
        bottomNavigationView.setOnNavigationItemSelectedListener(this)
        bottomNavigationView.selectedItemId = when (type) {
            DataType.LOG -> R.id.log_menu_item
            DataType.FUEL -> R.id.fuel_menu_item
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("item_id", bottomNavigationView.selectedItemId)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            super.onRestoreInstanceState(savedInstanceState)
        }
        bottomNavigationView.selectedItemId = savedInstanceState?.getInt("item_id") ?: R.id.log_menu_item
    }

    override fun onDestroy() {
        super.onDestroy()
        CarLogbookDatabase.closeDatabase()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        var result = false

        when (item.itemId) {
            R.id.log_menu_item -> {
                type = DataType.LOG
                loadDataFragment()
                result = true
            }
            R.id.fuel_menu_item -> {
                type = DataType.FUEL
                loadDataFragment()
                result = true
            }
            R.id.settings_menu_item -> {
                periodCalendar = Calendar.getInstance()
                loadFragment(SettingsFragment())
                result = true
            }
        }
        return result
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frame_container, fragment)
            .commit()
    }

    private fun loadFragment() {
        when (bottomNavigationView.selectedItemId) {
            R.id.log_menu_item -> loadDataFragment()
            R.id.fuel_menu_item -> loadDataFragment()
            R.id.settings_menu_item -> loadFragment(SettingsFragment())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                PERIOD_DIALOG_RESULT -> loadFragment()
                CAR_PROFILE_SHOW_LIST -> loadFragment()
                CAR_PROFILE_FIRST_START -> loadFragment()
                LOG_EXPORT -> exportDataToPdfFile(data?.data ?: Uri.EMPTY)
            }
        } else if (resultCode == RESULT_CANCELED && requestCode == CAR_PROFILE_SHOW_LIST) {
            loadFragment()
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat?,
        pref: Preference
    ): Boolean {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment)
        fragment.arguments = pref.extras
        fragment.setTargetFragment(caller, 0)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frame_container, fragment)
            .addToBackStack(null)
            .commit()

        return true
    }

    private fun checkCarProfile(preferences: SharedPreferences) {
        val dialogShown = preferences.getBoolean(getString(R.string.default_car_profile_dialog_key), false)
        if (profileId == -1L) {
            val entities = SelectAllCarProfileAsyncTask(CarLogbookDatabase.invoke(applicationContext)).execute().get()
            if (entities.isNotEmpty()) {
                profileId = entities[0].id!!
                profileName = entities[0].name
                preferences.edit()
                    .putLong(getString(R.string.car_profile_id_key), profileId)
                    .putString(getString(R.string.car_profile_name_key), profileName)
                    .apply()
                if (defaultCarProfileName == profileName && !dialogShown) {
                    createAlertDialog(
                        this,
                        R.string.default_car_profile_dialog_title,
                        R.string.default_car_profile_dialog_message
                    ).show()
                    preferences.edit()
                        .putBoolean(getString(R.string.default_car_profile_dialog_key), true)
                        .apply()
                }
            }
        }
    }

    private fun loadDataFragment() {
        if (profileId != -1L) {
            loadFragment(DataFragment())
        } else {
            startActivityForResult(Intent(applicationContext, FirstStartActivity::class.java), CAR_PROFILE_FIRST_START)
        }
    }

    private fun exportDataToPdfFile(directoryUri: Uri) {
        val data = LogRepository(applicationContext).selectAllByProfileId(profileId).sortedBy { it.time }
        val carData = CarProfileRepository(applicationContext).selectById(profileId)
        ExportToPdfAsyncTask(this, directoryUri, data, carData).execute()
    }
}
