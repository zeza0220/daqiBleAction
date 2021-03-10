package com.daqi.bleaction

import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import android.content.Context
import android.net.MacAddress
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.renderscript.Sampler
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.os.postDelayed
import com.daqi.bleaction.BluetoothUtils.bytesToHexString
import java.util.*
import kotlin.collections.ArrayList

//Gatt Service列表
val mGattServiceList = ArrayList<BluetoothGattService>()

class BleConnectionHelper(var mContext: Context,val macAddress:String) {

    //蓝牙设配器
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    //原生蓝牙对象
    private lateinit var mBluetoothDevice: BluetoothDevice
    //蓝牙管理类
    private lateinit var mBluetoothManager: BluetoothManager
    //标记是否连接
    var isConnected = false
    //标记重置次数
    private var retryCount = 0
    //蓝牙Gatt回调
    private lateinit var mGattCallback: daqiBluetoothGattCallback
    //蓝牙Gatt
    private lateinit var mBluetoothGatt: BluetoothGatt

    //工作子线程Handler
    private var mHandler:Handler
    //主线程回调Handler
    private var mMainHandler: Handler

    //特征值展示的布局
    private var mCharacteristicValueView: TextView? = null
    //描述值展示的布局
    private var mDescriptorValueView: TextView? = null

    //回调监听
    private var mListener:BleConnectionListener? = null

    init {
        mContext = mContext.applicationContext
        val handlerThread = HandlerThread("BleConnection")
        handlerThread.start()
        //初始化工作线程Handler
        mHandler = Handler(handlerThread.looper)
        //初始化主线程Handler
        mMainHandler = Handler(mContext.mainLooper)
        initBluetooth()
    }

    /**
      * 初始化蓝牙
      */
    private fun initBluetooth(){
        //初始化ble设配器
        mBluetoothManager = mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager.adapter
        mGattCallback = daqiBluetoothGattCallback()
    }

    /**
     * 连接
     */
    fun connection(){
        if (!isConnected){
            //获取原始蓝牙设备对象进行连接
            mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAddress)
            //重置连接重试次数
            retryCount = 0
            //连接设备
            mHandler.post{
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mBluetoothGatt = mBluetoothDevice.connectGatt(mContext, false,
                        mGattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    mBluetoothGatt = mBluetoothDevice.connectGatt(mContext, false, mGattCallback)
                }
            }
        }
    }

    /**
     * 发现服务
     */
    fun discoverServices(){
        if (isConnected){
            mHandler.post {
                //发现服务
                mBluetoothGatt.discoverServices()
            }
        }
    }

    /**
      * 读取特征
      */
    fun readCharacteristic(characteristic:BluetoothGattCharacteristic,characteristicValueView:TextView){
        if (isConnected) {
            mHandler.post {
                mCharacteristicValueView = characteristicValueView
                mBluetoothGatt.readCharacteristic(characteristic)
            }
        }
    }

    /**
     * 写入特征
     */
    fun writeCharacteristic(characteristic:BluetoothGattCharacteristic,byteArray: ByteArray){
        //在客户端发送前设置 写入不响应 ，可以直接得到写入返回，但还是会触发服务端的onCharacteristicWriteRequest
        //characteristic.writeType = WRITE_TYPE_NO_RESPONSE
        //在发送之前，先设置特征通知.
        //mBluetoothGatt.setCharacteristicNotification(characteristic, true)
        //官网上还有一个将该特征下的描述设置为 通知数值 的步骤，有些硬件需要有些不需要，看具体情况
        /*val descriptor = characteristic.getDescriptor(UUID.fromString(NotificationDescriptorUUID))
        descriptor.let {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            mBluetoothGatt.writeDescriptor(descriptor)
        }*/
        //发送十六进制字符串的字节给连接的ble设备，也不一定是规定16进制的，只要对面能解析得出来。
        characteristic.value = byteArray
        mBluetoothGatt.writeCharacteristic(characteristic)
    }

    /**
     * 设置特征通知
     */
    fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic){
        mBluetoothGatt.setCharacteristicNotification(characteristic, true)
    }

    /**
     * 读取描述
     */
    fun readDescriptor(descriptor:BluetoothGattDescriptor,descriptorValueView:TextView){
        if (isConnected) {
            mHandler.post {
                mDescriptorValueView = descriptorValueView
                mBluetoothGatt.readDescriptor(descriptor)
            }
        }
    }

    /**
     * 写入描述
     */
    fun writeDescriptor(descriptor:BluetoothGattDescriptor,byteArray: ByteArray){
        descriptor.value = byteArray
        mBluetoothGatt.writeDescriptor(descriptor)
    }

    fun onDestroy() {
        //清空消息
        mHandler.removeCallbacks(null)
        mMainHandler.removeCallbacks(null)
        mHandler.looper.quit()

        if (isConnected){
            //断开连接
            closeConnection()
        }
        //清空服务列表
        mGattServiceList.clear()
    }

    /**
      * 断开连接，会触发onConnectionStateChange回调，在onConnectionStateChange回调中调用mBluetoothGatt.close()
      */
    fun disConnection(){
        if (isConnected) {
            isConnected = false
            mBluetoothGatt.disconnect()
        }
    }

    /**
     * 彻底关闭连接，不带onConnectionStateChange回调的
     */
    private fun closeConnection() {
        if (isConnected) {
            isConnected = false
            mBluetoothGatt.disconnect()
            //调用close()后，连接时传入callback会被置空，无法得到断开连接时onConnectionStateChange（）回调
            mBluetoothGatt.close()
        }
    }

    /**
      * 尝试重连
      */
    private fun tryReConnection(){
        retryCount++
        //之前尝试连接不成功，先关闭之前的连接
        closeConnection()
        //延迟500ms再重新尝试连接
        mHandler.postDelayed(500.toLong()){
            //获取原始蓝牙设备对象进行连接
            mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAddress)
            //连接设备
            mHandler.post{
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mBluetoothGatt = mBluetoothDevice.connectGatt(mContext, false,
                        mGattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    mBluetoothGatt = mBluetoothDevice.connectGatt(mContext, false, mGattCallback)
                }
            }
        }
    }

    /**
      * 处理连接状态改变
      */
    private fun connectionStateChange(status: Int,newState: Int){
        Log.d("daqia","Client  status = $status  newState = $newState")
        //断开连接或者连接成功时status = GATT_SUCCESS
        when(status){
            BluetoothGatt.GATT_SUCCESS -> {
                //连接状态
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true
                    //连接成功回调
                    mMainHandler.post{
                        if(mListener != null){
                            mListener?.onConnectionSuccess()
                        }
                    }
                    //发现服务
                    mHandler.postDelayed(500.toLong()){
                        //发现服务
                        mBluetoothGatt.discoverServices()
                    }
                }else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                    isConnected = false
                    //断开连接回调
                    mMainHandler.post{
                        if(mListener != null){
                            mListener?.disConnection()
                        }
                    }
                    //断开连接状态
                    mBluetoothGatt.close()
                }
            }
            else -> {
                //遇到特殊情况尝试重连，增加连接成功率
                if (retryCount < 1 && !isConnected){
                    //尝试重连
                    tryReConnection()
                }else{
                    mMainHandler.post{
                        //判断是否连接上
                        if (isConnected){
                            //异常连接断开
                            if(mListener != null){
                                mListener?.disConnection()
                            }
                        }else{
                            //连接失败回调
                            if(mListener != null){
                                mListener?.onConnectionFail()
                            }
                        }
                    }
                    //断开连接
                    closeConnection()
                }
            }
        }
    }

    /**
      * 处理发现服务
      */
    private fun servicesDiscovered(status:Int){
        when(status){
            BluetoothGatt.GATT_SUCCESS -> {
                //清空之前的Service列表
                mGattServiceList.clear()
                //重新设置Service列表
                mGattServiceList.addAll(mBluetoothGatt.getServices())
                mMainHandler.post{
                    //发现服务回调
                    if(mListener != null){
                        mListener?.discoveredServices()
                    }
                }
            }
            else ->{
                //发现服务失败
                ToastUtils.showBottomToast(mContext,"发现服务失败")
            }
        }
    }

    /**
      * 特征读取
      */
    private fun characteristicRead(status:Int,characteristic: BluetoothGattCharacteristic?){
        when(status){
            BluetoothGatt.GATT_SUCCESS -> {
                characteristic?.let { characteristic ->
                    val stringBuilder = StringBuilder()
                    val value = bytesToHexString(characteristic.value)
                    stringBuilder.append("特征读取 CharacteristicUUID = ${characteristic.uuid.toString()}")
                    stringBuilder.append(" ,特征值 = ${value}")
                    mMainHandler.post{
                        //读取特征值回调
                        if(mListener != null){
                            mListener?.readCharacteristic(stringBuilder.toString())
                        }
                        //设置并显示特征值
                        mCharacteristicValueView?.let {
                            it.visibility = View.VISIBLE
                            if (value != null && !value.equals("")){
                                it.text = "Value:(0x)$value"
                            }else{
                                it.text = "Value:"
                            }
                        }
                        //置空
                        mCharacteristicValueView = null
                    }
                }
            }
            //无可读权限
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                mMainHandler.post{
                    //读取特征值回调
                    if(mListener != null){
                        mListener?.readCharacteristic("无读取权限")
                    }
                }
                //置空
                mCharacteristicValueView = null
            }
            else -> {
                mMainHandler.post{
                    //读取特征值回调
                    if(mListener != null){
                        mListener?.readCharacteristic("特征读取失败 status = $status")
                    }
                }
                //置空
                mCharacteristicValueView = null
            }
        }
    }

    /**
      * 特征写入
      */
    private fun characteristicWrite(status:Int,characteristic: BluetoothGattCharacteristic?){
        when(status){
             BluetoothGatt.GATT_SUCCESS -> {
                 characteristic?.let {characteristic ->
                     val stringBuilder = StringBuilder()
                     stringBuilder.append("特征写入 CharacteristicUUID = ${characteristic.uuid.toString()}")
                     stringBuilder.append(" ,写入值 = ${bytesToHexString(characteristic.value)}")
                     mMainHandler.post{
                         //读取特征值回调
                         if(mListener != null){
                             mListener?.writeCharacteristic(stringBuilder.toString())
                         }
                     }
                 }
             }
            else -> {
                //特征写入失败
                mMainHandler.post{
                    //读取特征值回调
                    if(mListener != null){
                        mListener?.writeCharacteristic("特征写入失败 status = $status")
                    }
                }
            }
        }
    }

    /**
      * 特征改变
      */
    private fun characteristicChanged(characteristic: BluetoothGattCharacteristic?){
        characteristic?.let {characteristic ->
            val stringBuilder = StringBuilder()
            stringBuilder.append("特征改变 CharacteristicUUID = ${characteristic.uuid.toString()}")
            stringBuilder.append(" ,改变值 = ${bytesToHexString(characteristic.value)}")
            mMainHandler.post{
                //读取特征值回调
                if(mListener != null){
                    mListener?.characteristicChange(stringBuilder.toString())
                }
            }
        }
    }

    /**
     * 描述写入
     */
    private fun descriptorWrite(status:Int,descriptor: BluetoothGattDescriptor?){
        when(status){
            BluetoothGatt.GATT_SUCCESS -> {
                descriptor?.let { descriptor ->
                    val stringBuilder = StringBuilder()
                    stringBuilder.append("描述写入 DescriptorUUID = ${descriptor.uuid.toString()}")
                    stringBuilder.append(" ,写入值 = ${bytesToHexString(descriptor.value)}")
                    mMainHandler.post{
                        //读取特征值回调
                        if(mListener != null){
                            mListener?.writeDescriptor(stringBuilder.toString())
                        }
                    }
                }
            }
            else -> {
                //描述写入失败
                mMainHandler.post{
                    //读取特征值回调
                    if(mListener != null){
                        mListener?.writeDescriptor("描述写入失败 status = $status")
                    }
                }
            }
        }
    }

    /**
      * 描述读取
      */
    private fun descriptorRead(status:Int,descriptor: BluetoothGattDescriptor?){
        when(status){
            //操作成功
            BluetoothGatt.GATT_SUCCESS -> {
                descriptor?.let { descriptor ->
                    val value = bytesToHexString(descriptor.value)
                    val stringBuilder = StringBuilder()
                    stringBuilder.append("描述读取 DescriptorUUID = ${descriptor.uuid.toString()}")
                    stringBuilder.append(" ,描述值 = ${value}")
                    mMainHandler.post{
                        //读取特征值回调
                        if(mListener != null){
                            mListener?.readDescriptor(stringBuilder.toString())
                        }
                        //设置并显示描述值
                        mDescriptorValueView?.let {
                            it.visibility = View.VISIBLE
                            if (value != null && !value.equals("")){
                                it.text = "Value:(0x)$value"
                            }else{
                                it.text = "Value:"
                            }
                        }
                        //置空
                        mDescriptorValueView = null
                    }
                }
            }
            //无可读权限
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                mMainHandler.post{
                    //读取特征值回调
                    if(mListener != null){
                        mListener?.readDescriptor("无读取权限")
                    }
                }
                //置空
                mDescriptorValueView = null
            }
            else -> {
                mMainHandler.post{
                    //读取特征值回调
                    if(mListener != null){
                        mListener?.readDescriptor("读取描述失败 status = $status")
                    }
                }
                //置空
                mDescriptorValueView = null
            }
        }
    }


    /**
      * Gatt回调
      */
    private inner class daqiBluetoothGattCallback : BluetoothGattCallback() {
        //连接状态改变回调
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            //抛给工作线程处理，尽快返回
            mHandler.post {
                connectionStateChange(status,newState)
            }
        }

        //服务发现回调
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            //抛给工作线程处理
            mHandler.post {
                servicesDiscovered(status)
            }
        }

        //特征读取回调
        override fun onCharacteristicRead(gatt: BluetoothGatt?,
                                          characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            //抛给工作线程处理
            mHandler.post {
                characteristicRead(status,characteristic)
            }
        }

        //特征写入回调
        override fun onCharacteristicWrite(gatt: BluetoothGatt?,
                                           characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            //抛给工作线程处理
            mHandler.post {
                characteristicWrite(status,characteristic)
            }
        }

        //特征改变回调（主要由外设回调）
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            //抛给工作线程处理
            mHandler.post {
                characteristicChanged(characteristic)
            }
        }

        //描述写入回调
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            //抛给工作线程处理
            mHandler.post {
                descriptorWrite(status,descriptor)
            }
        }

        //描述读取回调
        override fun onDescriptorRead(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorRead(gatt, descriptor, status)
            //抛给工作线程处理
            mHandler.post {
                descriptorRead(status,descriptor)
            }
        }
    }

    /**
      * 监听回调
      */
    interface BleConnectionListener{
        //连接成功
        fun onConnectionSuccess()
        //连接失败
        fun onConnectionFail()
        //断开连接
        fun disConnection()
        //发现服务
        fun discoveredServices()
        //读取特征值
        fun readCharacteristic(data:String)
        //写入特征值
        fun writeCharacteristic(data:String)
        //读取描述
        fun readDescriptor(data:String)
        //写入描述
        fun writeDescriptor(data:String)
        //写入特征值
        fun characteristicChange(data:String)
    }

    fun setBleConnectionListener(listener:BleConnectionListener){
        mListener = listener
    }

}