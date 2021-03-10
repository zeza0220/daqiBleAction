package com.daqi.bleaction

import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import com.daqi.bleaction.BluetoothUtils.*
import kotlinx.android.synthetic.main.bleserver_page_fragment.*
import java.util.*

//规定的
val NotificationDescriptorUUID = "00002902-0000-1000-8000-00805f9b34fb"

//服务 UUID
var UUID_SERVICE = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
var UUID_SERVICE2 = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

//特征 UUID
var UUID_CHARACTERISTIC = UUID.fromString("0000ff11-0000-1000-8000-00805f9b34fb")
var UUID_CHARACTERISTIC2 = UUID.fromString("0000ff12-0000-1000-8000-00805f9b34fb")

//描述 UUID
var UUID_DESCRIPTOR = UUID.fromString(NotificationDescriptorUUID)


class BleServerPageFragment :Fragment() {

    //广播时间(设置为0则持续广播)
    private val mTime = 0

    //蓝牙管理类
    private lateinit var mBluetoothManager: BluetoothManager
    //蓝牙设配器
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    //GattService
    private lateinit var mGattService: BluetoothGattService
    //GattCharacteristic
    private lateinit var mGattCharacteristic: BluetoothGattCharacteristic
    //只读的GattCharacteristic
    private lateinit var mGattReadCharacteristic: BluetoothGattCharacteristic
    //GattDescriptor
    private lateinit var mGattDescriptor: BluetoothGattDescriptor

    //BLE广播操作类
    private var mBluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    //蓝牙广播回调类
    private lateinit var mAdvertiseCallback: daqiAdvertiseCallback
    //广播设置(必须)
    private lateinit var mAdvertiseSettings: AdvertiseSettings
    //广播数据(必须，广播启动就会发送)
    private lateinit var mAdvertiseData: AdvertiseData
    //扫描响应数据(可选，当客户端扫描时才发送)
    private lateinit var mScanResponseData: AdvertiseData

    //GattServer回调
    private lateinit var mBluetoothGattServerCallback: BluetoothGattServerCallback
    //GattServer
    private lateinit var mBluetoothGattServer: BluetoothGattServer

    companion object{
        fun newInstance() = BleServerPageFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = LayoutInflater.from(activity).inflate(
            R.layout.bleserver_page_fragment,container,false)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        //初始化监听
        initListener()
        //初始化蓝牙
        initBluetooth()
    }

    /**
     * 初始化蓝牙
     */
    private fun initBluetooth(){
        //初始化ble设配器
        mBluetoothManager = activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager.adapter
        //初始化蓝牙回调包
        mAdvertiseCallback = daqiAdvertiseCallback()
    }

    /**
      * 初始化组件
      */
    private fun initListener(){
        //设置发送广播按钮监听
        startAdvertisingBtn.setOnClickListener {
            startAdvertising()
        }
        //设置停止广播按钮监听
        stopAdvertisingBtn.setOnClickListener {
            stopAdvertising()
        }
        //设置添加服务按钮监听
        addGattServerBtn.setOnClickListener {
            addGattServer()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //请求打开蓝牙的响应码
        if (requestCode == OpenBluetooth_Request_Code){
            when(resultCode){
                Activity.RESULT_OK -> {
                    //蓝牙开启后，开启广播
                    startAdvertising()
                }
                else -> {
                    Toast.makeText(activity,"请打开蓝牙进行调试",Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
      * 发送广播
      */
    private fun startAdvertising(){
        //判断蓝牙是否开启，如果关闭则请求打开蓝牙
        if (!mBluetoothAdapter.isEnabled()){
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, OpenBluetooth_Request_Code)
            return
        }
        //设置蓝牙广播名称
        mBluetoothAdapter.name = "zaza ble test"

        //初始化广播设置
        mAdvertiseSettings = AdvertiseSettings.Builder()
            //设置广播模式，以控制广播的功率和延迟。 ADVERTISE_MODE_LOW_LATENCY为高功率，低延迟
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            //设置蓝牙广播发射功率级别
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            //广播时限。最多180000毫秒。值为0将禁用时间限制。（不设置则为无限广播时长）
            .setTimeout(mTime)
            //设置广告类型是可连接还是不可连接。
            .setConnectable(true)
            .build()

        //设置广播报文
        mAdvertiseData = AdvertiseData.Builder()
            //设置广播包中是否包含设备名称。
            .setIncludeDeviceName(true)
            //设置广播包中是否包含发射功率
            .setIncludeTxPowerLevel(true)
            //设置UUID
            .addServiceUuid(ParcelUuid(UUID_SERVICE))
            .addServiceUuid(ParcelUuid(UUID_SERVICE2))
            .build()

        //设置广播扫描响应报文(可选)
        mScanResponseData = AdvertiseData.Builder()
            //自定义服务数据，将其转化为字节数组传入
            .addServiceData(ParcelUuid(UUID_SERVICE2), byteArrayOf(2,3,4))
            //设备厂商自定义数据，将其转化为字节数组传入
            .addManufacturerData(0x06, byteArrayOf(1,2,3))
            .build()

        //获取BLE广播操作对象
        //官网建议获取mBluetoothLeAdvertiser时，先做mBluetoothAdapter.isMultipleAdvertisementSupported判断，
        // 但部分华为手机支持Ble广播却还是返回false,所以最后以mBluetoothLeAdvertiser是否不为空且蓝牙打开为准
        mBluetoothLeAdvertiser = mBluetoothAdapter.bluetoothLeAdvertiser
        //蓝牙关闭或者不支持
        if (mBluetoothLeAdvertiser != null && mBluetoothAdapter.isEnabled()){
            Log.d("daqia","mBluetoothLeAdvertiser != null = ${mBluetoothLeAdvertiser != null} " +
                    "mBluetoothAdapter.isMultipleAdvertisementSupported = ${mBluetoothAdapter.isMultipleAdvertisementSupported}")
            //开始广播（不附带扫描响应报文）
            //mBluetoothLeAdvertiser.startAdvertising(mAdvertiseSettings, mAdvertiseData, mAdvertiseCallback)
            //开始广播（附带扫描响应报文）
            mBluetoothLeAdvertiser?.startAdvertising(mAdvertiseSettings, mAdvertiseData,
                mScanResponseData, mAdvertiseCallback)
        }else{
            //前面已经确保在蓝牙开启时才广播，排除蓝牙未开启
            displayInfo("该手机芯片不支持BLE广播")
        }
    }

    /**
      * 停止广播
      */
    private fun stopAdvertising(){
        mBluetoothLeAdvertiser?.let {advertiser ->
            advertiser.stopAdvertising(mAdvertiseCallback)
            displayInfo("停止Ble广播")
        }
    }

    /**
      * 添加Gatt 服务和特征
      * 广播是广播，只有添加Gatt服务和特征后，连接才有服务和特征用于数据交换
      */
    private fun addGattServer(){
        //初始化Service
        //创建服务，并初始化服务的UUID和服务类型。
        //BluetoothGattService.SERVICE_TYPE_PRIMARY 为主要服务类型
        mGattService = BluetoothGattService(UUID_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        //初始化特征(添加读写权限)
        //在服务端配置特征时，设置BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        //那么onCharacteristicWriteRequest()回调时，不需要GattServer进行response才能进行响应。
        mGattCharacteristic = BluetoothGattCharacteristic(UUID_CHARACTERISTIC,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_READ,
            (BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ))
        mGattCharacteristic.value = byteArrayOf(17,2,85)

        //设置只读的特征 （只写同理）
        mGattReadCharacteristic = BluetoothGattCharacteristic(UUID_CHARACTERISTIC2,
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)

        //初始化描述
        mGattDescriptor = BluetoothGattDescriptor(UUID_DESCRIPTOR,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        mGattDescriptor.setValue(byteArrayOf(12,33,54,45))

        //Service添加特征值
        mGattService.addCharacteristic(mGattCharacteristic)
        mGattService.addCharacteristic(mGattReadCharacteristic)
        //特征值添加描述
        mGattCharacteristic.addDescriptor(mGattDescriptor)
        //初始化GattServer回调
        mBluetoothGattServerCallback = daqiBluetoothGattServerCallback()

        //添加服务
        if (mBluetoothManager != null)
            mBluetoothGattServer = mBluetoothManager.openGattServer(activity, mBluetoothGattServerCallback)
        mBluetoothGattServer.addService(mGattService)
    }

    /**
     * 展示信息
     */
    private fun displayInfo(infoStr:String){
        displayView.post {
            displayView.text = displayView.text.toString() + "\n $infoStr"
        }
    }

    /**
     * 蓝牙广播回调类
     */
    private inner class daqiAdvertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            //如果持续广播 则不提醒关闭
            if (mTime != 0){
                //设置关闭提醒
                startAdvertisingBtn.postDelayed(Runnable {
                    displayInfo("Ble广播结束")
                }, mTime.toLong())
            }
            val advertiseInfo = StringBuffer("启动Ble广播成功")
            //连接性
            if (settingsInEffect.isConnectable){
                advertiseInfo.append(", 可连接")
            }else{
                advertiseInfo.append(", 不可连接")
            }
            //广播时长
            if (settingsInEffect.timeout == 0){
                advertiseInfo.append(", 持续广播")
            }else{
                advertiseInfo.append(", 广播时长 ${settingsInEffect.timeout} ms")
            }
            displayInfo(advertiseInfo.toString())
        }

        //具体失败返回码可以到官网查看
        override fun onStartFailure(errorCode: Int) {
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE){
                displayInfo("启动Ble广播失败 数据报文超出31字节")
            }else{
                displayInfo("启动Ble广播失败 errorCode = $errorCode")
            }
        }
    }

    /**
     * GattServer回调
     */
    private inner class daqiBluetoothGattServerCallback : BluetoothGattServerCallback() {

        //设备连接/断开连接回调
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.d("daqia","Server status = $status  newState = $newState")
            if (status == BluetoothGatt.GATT_SUCCESS){
                //连接成功
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    displayInfo("${device.address} 连接成功")
                }else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                    displayInfo("${device.address} 断开连接")
                }
            }else{
                displayInfo("onConnectionStateChange status = $status newState = $newState")
            }
        }

        //添加本地服务回调
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                displayInfo("添加Gatt服务成功 UUUID = ${service.uuid}")
            } else {
                displayInfo("添加Gatt服务失败")
            }
        }

        //特征值读取回调
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int,
                                                 characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            // 响应客户端
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                offset, characteristic.value)
            displayInfo("${device.address} 请求读取特征值:  UUID = ${characteristic.uuid} " +
                    "读取值 = ${bytesToHexString(characteristic.value)}")
        }

        //特征值写入回调
        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int,
                                                  characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean,
                                                  responseNeeded: Boolean, offset: Int, value: ByteArray) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            //刷新该特征值
            characteristic.value = value
            // 响应客户端
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                offset, value)
            displayInfo("${device.address} 请求写入特征值:  UUID = ${characteristic.uuid} " +
                    "写入值 = ${bytesToHexString(value)}")

            //模拟数据处理，延迟100ms
            displayView.postDelayed(100) {
                //改变特征值
                characteristic.value = hexStringToBytes("abcdef")
                //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                mBluetoothGattServer.notifyCharacteristicChanged(device,characteristic,false)
            }
        }

        //描述读取回调
        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int,
                                             descriptor: BluetoothGattDescriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            // 响应客户端
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                offset,descriptor.value)
            displayInfo("${device.address} 请求读取描述值:  UUID = ${descriptor.uuid} " +
                    "读取值 = ${bytesToHexString(descriptor.value)}")
        }

        //描述写入回调
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
                                              preparedWrite: Boolean, responseNeeded: Boolean,
                                              offset: Int, value: ByteArray) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            //刷新描述值
            descriptor.value = value
            // 响应客户端
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                offset, value)

            displayInfo("${device.address} 请求写入描述值:  UUID = ${descriptor.uuid} " +
                    "写入值 = ${bytesToHexString(value)}")
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                displayInfo("${device?.address} 通知发送成功")
            }else{
                displayInfo("${device?.address} 通知发送失败 status = $status")
            }

        }
    }
}