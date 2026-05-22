viewModel.debugStatus.observe(viewLifecycleOwner) { status ->
    binding.tvDebugStatus.visibility = android.view.View.VISIBLE
    binding.tvDebugStatus.text = status
}
