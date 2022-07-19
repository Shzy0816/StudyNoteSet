package suyuan.edu.deliveryapp.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import me.yokeyword.fragmentation.SupportFragment
import me.yokeyword.fragmentation_swipeback.SwipeBackFragment

/**
 * 对viewBinding进行封装，只需要直接调用binding即可
 * 同时在onDestroyView中对其进行释放，无需书写冗余模板代码
 * 初始化的内容放在init()即可
 */
abstract class BaseFragment<T : ViewBinding> : SupportFragment() {

    private var _binding: T? = null
    val binding: T get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflaterViewBinding(inflater, container)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        init(view, savedInstanceState)
        super.onViewCreated(view, savedInstanceState)
    }


    /**
     * 在onViewCreated中进行初始化代码
     */
    abstract fun init(
        view: View,
        savedInstanceState: Bundle?
    )

    /**
     * 返回ViewBinding的内容就好，不使用反射
     */
    abstract fun inflaterViewBinding(inflater: LayoutInflater, container: ViewGroup?): T

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}