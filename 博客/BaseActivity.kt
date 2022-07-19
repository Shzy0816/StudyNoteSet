package suyuan.edu.deliveryapp.base

import android.os.Bundle
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import me.yokeyword.fragmentation.SupportActivity

abstract class BaseActivity<T : ViewBinding> : SupportActivity() {
    private var _binding: T? = null
    val binding: T get() = _binding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflaterViewBinding(layoutInflater)
        setContentView(binding.root)
        init(savedInstanceState)
    }

    /**
     * 返回ViewBinding的内容就好，不使用反射
     */
    abstract fun inflaterViewBinding(inflater: LayoutInflater): T

    /**
     * 在onCreate中进行初始化代码
     */
    abstract fun init(savedInstanceState: Bundle?)

}