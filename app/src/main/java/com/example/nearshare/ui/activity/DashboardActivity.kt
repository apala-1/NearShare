package com.example.nearshare.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.nearshare.R
import com.example.nearshare.databinding.ActivityDashboardBinding
import com.example.nearshare.ui.Fragment.HomeFragment
import com.google.android.material.navigation.NavigationView

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val homeFragment = HomeFragment()

        makeCurrentFragment(homeFragment)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun makeCurrentFragment(fragment: HomeFragment) =
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.container, fragment)
            commit()
        }


    override fun onNavigationItemSelected(it: MenuItem): Boolean {
        when(it.itemId){
            R.id.home -> makeCurrentFragment(HomeFragment())
        }
        return true
    }

}