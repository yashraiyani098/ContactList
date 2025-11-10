package com.yash.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yash.demo.databinding.ActivityMainBinding
import com.yash.demo.model.Contact

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val sectionPositions = mutableMapOf<Char, Int>()
    private var highlightedView: TextView? = null
    private var highlightedLetter: Char? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            sectionPositions.toSortedMap().entries.findLast {
                (binding.contactListContainer.getChildAt(it.value)?.top
                    ?: 0) <= binding.scrollView.scrollY
            }?.key?.let {
                binding.stickyHeader.text = it.toString()
                binding.stickyHeader.visibility = View.VISIBLE
                updateSidebarHighlight(it)
            } ?: run { binding.stickyHeader.visibility = View.GONE }
        }

        setupAlphabetSidebar()
        checkPermissionAndLoadContacts()
    }

    private fun checkPermissionAndLoadContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 100)
        } else loadContacts()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            loadContacts()
    }

    private fun loadContacts() {
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val contacts = mutableListOf<Contact>()
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                cursor.getString(nameIndex)?.takeIf { it.isNotEmpty() }?.let { name ->
                    contacts.add(Contact(name, cursor.getString(numberIndex) ?: "", name[0].uppercaseChar()))
                }
            }
            displayContacts(contacts)
        }
    }

    private fun displayContacts(contacts: List<Contact>) {
        binding.contactListContainer.removeAllViews()
        sectionPositions.clear()
        var currentSection: Char? = null

        contacts.forEach { contact ->
            if (contact.initial != currentSection) {
                currentSection = contact.initial
                sectionPositions[currentSection] = binding.contactListContainer.childCount
                LayoutInflater.from(this).inflate(R.layout.contact_section_header, binding.contactListContainer, false).apply {
                    findViewById<TextView>(R.id.sectionLetter).text = currentSection.toString()
                    binding.contactListContainer.addView(this)
                }
            }
            LayoutInflater.from(this).inflate(R.layout.contact_item, binding.contactListContainer, false).apply {
                findViewById<TextView>(R.id.contactName).text = contact.name
                findViewById<TextView>(R.id.contactNumber).text = contact.phoneNumber
                findViewById<TextView>(R.id.contactAvatar).text = contact.initial.toString()
                binding.contactListContainer.addView(this)
            }
        }
    }

    private fun updateSidebarHighlight(letter: Char) {
        if (highlightedLetter == letter) return
        highlightedLetter = letter
        highlightedView?.apply {
            setBackgroundResource(0)
            setTextColor(ContextCompat.getColor(context, R.color.sidebar_text))
        }
        (letter - 'A').takeIf { it in 0 until binding.alphabetSidebar.childCount }?.let {
            (binding.alphabetSidebar.getChildAt(it) as TextView).apply {
                setBackgroundResource(R.drawable.circle_background)
                setTextColor(ContextCompat.getColor(context, R.color.white))
                highlightedView = this
            }
        }
    }

    private fun setupAlphabetSidebar() {
        ('A'..'Z').forEach { letter ->
            binding.alphabetSidebar.addView(TextView(this).apply {
                text = letter.toString()
                textSize = 12f
                setPadding(8, 4, 8, 4)
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.sidebar_text))
            })
        }

        binding.alphabetSidebar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val index = (event.y / (v.height / binding.alphabetSidebar.childCount)).toInt()
                        .coerceIn(0, binding.alphabetSidebar.childCount - 1)
                    val letter = (binding.alphabetSidebar.getChildAt(index) as TextView).text[0]
                    updateSidebarHighlight(letter)
                    sectionPositions[letter]?.let {
                        binding.contactListContainer.getChildAt(it)?.let { view ->
                            binding.scrollView.smoothScrollTo(0, view.top)
                        }
                    }
                    binding.currentLetterText.text = letter.toString()
                    binding.currentLetterText.visibility = View.VISIBLE
                    true
                }
                MotionEvent.ACTION_UP -> {
                    binding.currentLetterText.visibility = View.GONE
                    true
                }
                else -> false
            }
        }
    }
}