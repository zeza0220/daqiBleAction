package com.daqi.bleaction

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.os.postDelayed
import com.daqi.bleaction.bean.BleDevice

//扫描到的设备列表
public val mBleDeviceList = ArrayList<BleDevice>()

class BleScanHelper(context: Context){

    //标记当前是否在扫描
    private var mScanning = false
    //工作子线程
    private lateinit var mHandler: Handler
    //主线程Handler
    private lateinit var mMainHandler: Handler
    //android 5.0 扫描对象
    private var mBleScanner: BluetoothLeScanner? = null
    //5.0 以下扫描回调对象
    private lateinit var mLeScanCallback: BluetoothAdapter.LeScanCallback
    //5.0及其以上扫描回调对象
    private lateinit var mScanCallback: ScanCallback
    //5.0扫描配置对象
    private lateinit var mScanSettings: ScanSettings
    //扫描过滤器列表
    private lateinit var mScanFilterList:ArrayList<ScanFilter>
    //蓝牙设配器
    private var mBluetoothAdapter:BluetoothAdapter

    //统一的扫描回调对象
    private var mListener:onScanListener? = null
    //统一的扫描回调接口
    interface onScanListener{
        fun onNext(device: BleDevice)

        fun onFinish()
    }

    fun setOnScanListener(listener:onScanListener){
        mListener = listener
    }

    init {
        //初始化ble设配器
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = manager.adapter
        //初始化Handler
        initHandler(context)

        //初始化蓝牙回调
        initBluetoothCallBack()
        //初始化蓝牙扫描配置
        initmScanSettings()
        //初始化蓝牙过滤器
        initScanFilter()
    }

    /**
     * 初始化Handler
     */
    private fun initHandler(context: Context){
        //初始化工作线程Handler
        val mHandlerThread = HandlerThread("ScanThread")
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
        //初始化主线程Handler
        mMainHandler = Handler(context.mainLooper)
    }

    /**
     * 初始化蓝牙回调
     */
    private fun initBluetoothCallBack(){
        //5.0及其以上扫描回调
        mScanCallback = object :ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                //post出去  尽快结束回调
                mMainHandler.post{
                    result?.let {
                        //扫描回调
                        mListener?.let { listener ->
                            val mBleDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                BleDevice(result.device,result.rssi,
                                    result.scanRecord.bytes,result.isConnectable,result.scanRecord)
                            } else {
                                BleDevice(result.device,result.rssi,
                                    result.scanRecord.bytes,scanRecord = result.scanRecord)
                            }
                            listener.onNext(mBleDevice)
                        }
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                when(errorCode){
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                        Log.e("daqia","扫描太频繁")
                }
            }
        }
        //5.0以下扫描回调
        mLeScanCallback =  object :BluetoothAdapter.LeScanCallback{
            override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
                mMainHandler.post{
                    if (device != null && scanRecord != null){
                        //扫描回调
                        mListener?.let {
                            val mBleDevice = BleDevice(device,rssi,scanRecord)
                            it.onNext(mBleDevice)
                        }
                    }
                }
            }
        }
    }

    /**
      * 初始化拦截器实现
     * 扫描回调只会返回符合该拦截器UUID的蓝牙设备
      */
    private fun initScanFilter(){
        mScanFilterList = ArrayList<ScanFilter>()
        val builder = ScanFilter.Builder()
        builder.setServiceUuid(ParcelUuid.fromString("0000fff1-0000-1000-8000-00805f9b34fb"))
        mScanFilterList.add(builder.build())
    }

    /**
     * 初始化蓝牙扫描配置
     */
    private fun initmScanSettings(){
        //创建ScanSettings的build对象用于设置参数
        val builder = ScanSettings.Builder()
                //设置功耗平衡模式
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            //设置高功耗模式
            //.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)

        //android 6.0添加设置回调类型、匹配模式等
        if(android.os.Build.VERSION.SDK_INT >= 23) {
            //定义回调类型
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            //设置蓝牙LE扫描滤波器硬件匹配的匹配模式
            builder.setMatchMode(ScanSettings.MATCH_MODE_STICKY)
        }
        //芯片组支持批处理芯片上的扫描
        if (mBluetoothAdapter.isOffloadedScanBatchingSupported()) {
            //设置蓝牙LE扫描的报告延迟的时间（以毫秒为单位）
            //设置为0以立即通知结果
            builder.setReportDelay(0L)
        }
        mScanSettings = builder.build()
    }

    /**
     * 开始扫描蓝牙ble
     */
    fun startScanBle(time:Int){
        if(!mBluetoothAdapter.isEnabled()) {
            return;
        }
        if (!mScanning){
            //android 5.0后
            if(android.os.Build.VERSION.SDK_INT >= 21) {
                //标记当前的为扫描状态
                mScanning = true
                //获取5.0新添的扫描类
                if (mBleScanner == null){
                    //mBLEScanner是5.0新添加的扫描类，通过BluetoothAdapter实例获取。
                    mBleScanner = mBluetoothAdapter.getBluetoothLeScanner()
                }
                //在子线程中扫描
                mHandler.post{
                    //mScanSettings是ScanSettings实例，mScanCallback是ScanCallback实例，后面进行讲解。
                    //过滤器列表传空，则可以扫描周围全部蓝牙设备
                    mBleScanner?.startScan(null,mScanSettings,mScanCallback)
                    //使用拦截器
                    //mBleScanner?.startScan(mScanFilterList,mScanSettings,mScanCallback)
                }
            } else {
                //标记当前的为扫描状态
                mScanning = true
                //5.0以下  开始扫描
                //在子线程中扫描
                mHandler.post{
                    //mLeScanCallback是BluetoothAdapter.LeScanCallback实例
                    mBluetoothAdapter.startLeScan(mLeScanCallback)
                }
            }

            //设置结束扫描
            mHandler.postDelayed(time.toLong()){
                //关闭ble扫描
                stopScanBle()
            }
        }
    }

    /**
     * 关闭ble扫描
     */
    fun stopScanBle(){
        if (mScanning){
            //移除之前的停止扫描post
            mHandler.removeCallbacks(null)
            //停止扫描设备
            if(android.os.Build.VERSION.SDK_INT >= 21) {
                //标记当前的为未扫描状态
                mScanning = false
                mBleScanner?.stopScan(mScanCallback)
            } else {
                //标记当前的为未扫描状态
                mScanning = false
                //5.0以下  停止扫描
                mBluetoothAdapter.stopLeScan(mLeScanCallback)
            }
            //主线程回调
            mMainHandler.post{
                //扫描结束回调
                mListener?.let {
                    it.onFinish()
                }
            }
        }
    }

    fun onDestroy() {
        mMainHandler.removeCallbacksAndMessages(null)
        mHandler.removeCallbacksAndMessages(null)
        mHandler.looper.quit()
    }

}