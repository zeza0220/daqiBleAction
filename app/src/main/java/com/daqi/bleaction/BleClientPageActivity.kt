package com.daqi.bleaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.daqi.bleaction.adapter.BleConnectionAdapter
import kotlinx.android.synthetic.main.bleclient_page_activity.*

private val Extra_MacAddress = "Extra_MacAddress"
private val Extra_Name = "Extra_Name"

class BleClientPageActivity: FragmentActivity() {

    private lateinit var mMacAddress:String
    //连接辅助类
    private lateinit var bleConnectionHelper:BleConnectionHelper
    //RecyclerView设配器
    private lateinit var mAdapter: BleConnectionAdapter

    companion object{
        fun newIntent(context: Context,macAddress:String,name: String):Intent{
            val intent = Intent(context,BleClientPageActivity::class.java)
            intent.putExtra(Extra_MacAddress,macAddress)
            intent.putExtra(Extra_Name,name)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bleclient_page_activity)
        //获取Intent数据
        mMacAddress = intent.getStringExtra(Extra_MacAddress)
        nameText.text = intent.getStringExtra(Extra_Name)
        //初始化蓝牙(必须要在组件初始化之前)
        initBluetooth()
        //初始化组件
        initView()

    }

    /**
      * 初始化组件
      */
    private fun initView(){
        //初始化下拉刷新布局
        mSwipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this,R.color.colorPrimary ))
        mSwipeRefreshLayout.setOnRefreshListener {
            //刷新服务
            bleConnectionHelper.discoverServices()
        }
        //禁止下拉
        mSwipeRefreshLayout.isEnabled = false
        //初始化RecyclerView
        serviceRecyclerView.layoutManager = LinearLayoutManager(this)
        mAdapter = BleConnectionAdapter(this,bleConnectionHelper)
        serviceRecyclerView.adapter = mAdapter
    }

    /**
      * 初始化蓝牙
      */
    private fun initBluetooth(){
        bleConnectionHelper = BleConnectionHelper(this,mMacAddress)
        bleConnectionHelper.setBleConnectionListener(daqiBleConnectionListener())
    }

    /**
      * 连接按钮点击监听方法
      */
    fun connectionBle(view: View){
        connectionBtn.isEnabled = false
        if(!bleConnectionHelper.isConnected){
            //连接
            bleConnectionHelper.connection()
        }else{
            //断开连接
            bleConnectionHelper.disConnection()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleConnectionHelper.onDestroy()
    }

    /**
      * 连接回调监听
      */
    private inner class daqiBleConnectionListener: BleConnectionHelper.BleConnectionListener {
        override fun onConnectionSuccess() {
            connectionBtn.text = "断开连接"
            connectionBtn.isEnabled = true
            //启动下拉
            mSwipeRefreshLayout.isEnabled = true
            ToastUtils.showBottomToast(this@BleClientPageActivity,"连接成功")
        }

        override fun onConnectionFail() {
            connectionBtn.isEnabled = true
            ToastUtils.showBottomToast(this@BleClientPageActivity,"连接失败")
        }

        override fun disConnection() {
            connectionBtn.text = "连接"
            connectionBtn.isEnabled = true
            //禁止下拉
            mSwipeRefreshLayout.isEnabled = false
            //清空列表
            mGattServiceList.clear()
            mAdapter.notifyDataSetChanged()
            ToastUtils.showBottomToast(this@BleClientPageActivity,"断开连接")
        }

        override fun discoveredServices() {
            //发现服务完成后
            if (mSwipeRefreshLayout.isRefreshing){
                mSwipeRefreshLayout.isRefreshing = false
            }
            //刷新列表
            mAdapter.notifyDataSetChanged()
            ToastUtils.showBottomToast(this@BleClientPageActivity,"发现服务")
        }

        override fun readCharacteristic(data: String) {
            ToastUtils.showBottomToast(this@BleClientPageActivity,data)
        }

        override fun writeCharacteristic(data: String) {
            ToastUtils.showBottomToast(this@BleClientPageActivity,data)
        }

        override fun readDescriptor(data: String) {
            ToastUtils.showBottomToast(this@BleClientPageActivity,data)
        }

        override fun writeDescriptor(data: String) {
            ToastUtils.showBottomToast(this@BleClientPageActivity,data)
        }

        override fun characteristicChange(data: String) {
            //ToastUtils.showBottomToast(this@BleClientPageActivity,data)
            //使用普通Toast  让写入回调Toast和特征改变Toast能完整展示
            Toast.makeText(this@BleClientPageActivity,data,Toast.LENGTH_SHORT).show()
        }
    }
}