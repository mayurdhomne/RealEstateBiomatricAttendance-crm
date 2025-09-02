package com.crm.realestate.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.crm.realestate.R

/**
 * Placeholder fragment - will be implemented in future tasks
 * Currently disabled to avoid compilation issues
 */
class EditProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val textView = TextView(requireContext())
        textView.text = "Edit Profile - Coming Soon"
        return textView
    }
}
