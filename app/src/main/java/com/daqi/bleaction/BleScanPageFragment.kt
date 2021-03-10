package com.daqi.bleaction

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.blescan_page_fragment.*
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.daqi.bleaction.BluetoothUtils.OpenBluetooth_Request_Code
import com.daqi.bleaction.BluetoothUtils.hasPermissions

import com.daqi.bleaction.adapter.BleScanAdapter
import com.daqi.bleaction.bean.BleDevice


class BleScanPageFragment: Fragment() {

    private val permissionLists = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    //RecyclerView设配器
    private lateinit var mAdapter: BleScanAdapter
    //蓝牙设配器
    private lateinit var mBluetoothAdapter:BluetoothAdapter
    //蓝牙扫描辅助类
    private lateinit var mBleScanHelper:BleScanHelper
    //扫描时间
    private var mScanTime = 2000

    companion object{
        fun newInstance() = BleScanPageFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var view = LayoutInflater.from(activity as Context).inflate(
            R.layout.blescan_page_fragment,container,false)
        //需要先返回view  布局id才可不为空
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        //初始化组件
        initView()
        //初始化蓝牙
        initBluetooth()
        //请求权限,并开始扫描
        startScan()
    }

    /**
      * 初始化组件
      */
    private fun initView(){
        //初始化下拉刷新布局
        mSwipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(
            activity as Context,R.color.colorPrimary ))
        mSwipeRefreshLayout.setOnRefreshListener {
            //清空之前扫描到的设备
            mBleDeviceList.clear()
            mAdapter.notifyDataSetChanged()
            //
            startScan()
        }
        //初始化列表组件
        mRecyclerView.layoutManager = LinearLayoutManager(activity)
        mAdapter = BleScanAdapter(activity as Context)
        mRecyclerView.adapter = mAdapter
    }

    /**
     * 初始化蓝牙
     */
    private fun initBluetooth(){
        //初始化ble设配器
        val manager = activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = manager.adapter
        //判断蓝牙是否开启，如果关闭则请求打开蓝牙
        if (!mBluetoothAdapter.isEnabled()){
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, OpenBluetooth_Request_Code)
        }
        //创建扫描辅助类
        mBleScanHelper = BleScanHelper(activity as Context)
        mBleScanHelper.setOnScanListener(object : BleScanHelper.onScanListener{
            override fun onNext(device: BleDevice) {
                //刷新ble设备列表
                refreshBleDeviceList(device)
            }

            override fun onFinish() {
                mSwipeRefreshLayout.isRefreshing = false
            }
        })
    }

    /**
     * 依据扫描结果刷新列表
     */
    private fun refreshBleDeviceList(mBleDevice: BleDevice){
        Log.d("daqia","name = ${mBleDevice.device.name ?: "null"} ,address = ${mBleDevice.device.address ?: "null"}")

        for (i in mBleDeviceList.indices) {
            //替换 刷新
            if (mBleDeviceList[i].device.address.equals(mBleDevice.device.address)){
                mBleDeviceList[i] = mBleDevice
                mAdapter.notifyItemChanged(i)
                return
            }
        }
        //添加  刷新
        mBleDeviceList.add(mBleDevice)
        //mAdapter.notifyDataSetChanged()
        mAdapter.notifyItemChanged(mBleDeviceList.size - 1)
    }

    /**
      * 请求蓝牙扫描权限并开始扫描
      */
    private fun startScan(){
        //android 6.0 动态请求权限
        if (android.os.Build.VERSION.SDK_INT >= 23){
            if (hasPermissions(activity as Context,permissionLists)){
                mBleScanHelper.startScanBle(mScanTime)
            }else{
                //请求扫描
                requestPermissions(permissionLists,17)
            }
        }else{
            mBleScanHelper.startScanBle(mScanTime)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mBleScanHelper.startScanBle(mScanTime)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //销毁扫描辅助类
        mBleScanHelper.onDestroy()
    }
}