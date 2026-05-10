package com.example.mealcamera.ui.shopping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.databinding.ActivityShoppingListBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ShoppingListFragment : Fragment() {

    private var _binding: ActivityShoppingListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ShoppingListAdapter
    private val repository by lazy { (requireActivity().application as MealCameraApplication).recipeRepository }
    private val currentUserId: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityShoppingListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        observeItems()
        setupActions()
    }

    private fun setupRecycler() {
        adapter = ShoppingListAdapter(
            onCheckedChanged = { item, checked ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.updateShoppingListItemChecked(item, checked)
                }
            },
            onRemoveItem = { item ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.deleteShoppingListItem(item)
                }
            }
        )
        binding.rvShoppingList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvShoppingList.adapter = adapter
    }

    private fun observeItems() {
        val userId = currentUserId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getShoppingListItems(userId).collect { items ->
                adapter.submitList(items)
                if (items.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.btnClearChecked.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.btnClearChecked.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupActions() {
        binding.btnClearChecked.setOnClickListener {
            val userId = currentUserId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                repository.clearCheckedShoppingListItems(userId)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}