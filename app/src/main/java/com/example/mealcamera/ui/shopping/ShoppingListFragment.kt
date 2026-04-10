package com.example.mealcamera.ui.shopping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
        setupToolbar()
        setupRecycler()
        observeItems()
        setupActions()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Список покупок"
        binding.toolbar.navigationIcon = null // В табах кнопка назад не нужна (п. 3 плана)
    }

    private fun setupRecycler() {
        adapter = ShoppingListAdapter { item, checked ->
            viewLifecycleOwner.lifecycleScope.launch { repository.updateShoppingListItemChecked(item, checked) }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun observeItems() {
        val userId = currentUserId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getShoppingListItems(userId).collect { items ->
                adapter.submitList(items)
                binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupActions() {
        binding.btnClearChecked.setOnClickListener {
            val userId = currentUserId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                repository.clearCheckedShoppingListItems(userId)
                Toast.makeText(requireContext(), "Отмеченные удалены", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}