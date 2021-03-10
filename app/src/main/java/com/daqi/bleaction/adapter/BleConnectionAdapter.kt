package com.daqi.bleaction.adapter

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_SECONDARY
import android.content.Context
import android.graphics.Color
import android.media.Image
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.daqi.bleaction.BleConnectionHelper
import com.daqi.bleaction.BluetoothUtils.hexStringToBytes
import com.daqi.bleaction.R
import com.daqi.bleaction.mGattServiceList

import com.daqi.bleaction.ToastUtils
import java.util.regex.Pattern


class BleConnectionAdapter(val context: Context,val bleConnectionHelper: BleConnectionHelper): RecyclerView.Adapter<BleConnectionAdapter.BleConnectionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleConnectionViewHolder {
        val view = LayoutInflater.from(context).inflate(
            R.layout.bleclient_list_item,parent,false)
        return BleConnectionViewHolder(view)
    }

    override fun getItemCount(): Int {
        return mGattServiceList.size
    }

    override fun onBindViewHolder(holder: BleConnectionViewHolder, position: Int) {
        holder.bindData(mGattServiceList[position])
    }

    inner class BleConnectionViewHolder(view:View):RecyclerView.ViewHolder(view){
        private var mContext: Context
        //GattService的名称
        private var serviceName:TextView
        //GattService的UUID
        private var uuidTextView:TextView
        //GattService的类型
        private var serviceType:TextView
        //service布局
        private var serviceLayout: ConstraintLayout
        //characteristic布局
        private var characteristicLayout:LinearLayout
        //二级服务布局
        private var secondaryServiceLayout:LinearLayout
        //隐藏布局
        private var otherLayout:FrameLayout

        init {
            mContext = itemView.context
            serviceName = itemView.findViewById(R.id.textView)
            uuidTextView = itemView.findViewById(com.daqi.bleaction.R.id.uuidTextView)
            serviceType = itemView.findViewById(com.daqi.bleaction.R.id.serviceType)
            serviceLayout = itemView.findViewById(com.daqi.bleaction.R.id.serviceLayout)
            characteristicLayout = itemView.findViewById(com.daqi.bleaction.R.id.characteristicLayout)
            secondaryServiceLayout = itemView.findViewById(com.daqi.bleaction.R.id.secondaryServiceLayout)
            otherLayout = itemView.findViewById(com.daqi.bleaction.R.id.otherLayout)
            serviceLayout.setOnClickListener {
                if (otherLayout.visibility == View.GONE){
                    otherLayout.visibility = View.VISIBLE
                }else{
                    otherLayout.visibility = View.GONE
                }
            }
        }

        fun bindData(service: BluetoothGattService){
            //设置服务的UUID
            uuidTextView.text = service.uuid.toString()
            //设置服务的类型
            when(service.type){
                SERVICE_TYPE_PRIMARY ->
                    serviceType.text = "PRIMARY SERVICE"
                SERVICE_TYPE_SECONDARY ->
                    serviceType.text = "SECONDARY SERVICE"
            }
            //移除原本全部的特征
            characteristicLayout.removeAllViews()

            when(service.uuid.toString()){
                "00001801-0000-1000-8000-00805f9b34fb" -> {
                    serviceName.text = "Generic Attribute"
                }
                "00001800-0000-1000-8000-00805f9b34fb" -> {
                    serviceName.text = "Generic Access"
                }
                else -> {
                    serviceName.text = "Service"
                    //遍历特征
                    service.characteristics?.let {characteristics ->
                        for (characteristic in characteristics) {
                            addCharacteristicLayout(characteristic)
                        }
                    }
                }
            }
        }

        /**
          * 添加
          */
        private fun addCharacteristicLayout(characteristic:BluetoothGattCharacteristic){
            val view = LayoutInflater.from(mContext).inflate(
                R.layout.bleclient_list_item_characteristic,characteristicLayout,false)
            //特征的uuiD
            val characteristicUUID:TextView = view.findViewById(R.id.characteristicUUID)
            //特征属性
            val characteristicProperties:TextView = view.findViewById(R.id.characteristicProperties)
            //读取特征值按钮
            val readCharacteristicBtn:ImageView = view.findViewById(R.id.readBtn)
            //写入特征值按钮
            val writeCharacteristicBtn:ImageView = view.findViewById(R.id.writeBtn)
            //特征值
            val characteristicValue:TextView = view.findViewById(R.id.characteristicValue)
            //描述Title
            val descriptorTitle:TextView = view.findViewById(R.id.descriptorsTitle)
            //描述布局
            val descriptorLayout:LinearLayout = view.findViewById(com.daqi.bleaction.R.id.descriptorLayout)

            //设置特征uuid
            characteristicUUID.text = characteristic.uuid.toString()
            //获取特征属性
            val propertiesStr = getProperties(characteristic.properties)
            //设置特征属性
            characteristicProperties.text = propertiesStr
            //根据特征属性显示特定的按钮和设置通知
            val propertiesArray = propertiesStr.split(",")
            for (propertieStr in propertiesArray) {
                when(propertieStr){
                    "READ" -> {
                        readCharacteristicBtn.visibility = View.VISIBLE
                        //设置读取按钮监听
                        readCharacteristicBtn.setOnClickListener {
                            //读取特征值
                            bleConnectionHelper.readCharacteristic(characteristic,characteristicValue)
                        }
                    }
                    "WRITE NO RESPONSE",
                    "WRITE" -> {
                        writeCharacteristicBtn.visibility = View.VISIBLE
                        //设置写入按钮监听
                        writeCharacteristicBtn.setOnClickListener {
                            showWriteDialog(characteristic)
                        }
                    }
                    //这里只是属性设置时，设置了通知属性，检查具有该属性顺手设置通知回调，实际还是在向特征发送数值前设置
                    "NOTIFY" -> {
                        //设置通知
                        bleConnectionHelper.setCharacteristicNotification(characteristic)
                    }
                }
            }

            //处理描述
            characteristic.descriptors?.let {descriptors ->
                if (descriptors.size > 0){
                    //在描述中获取到权限值为0,无法获取该描述具体的读取权限，只能通过规定的uuid进行判断
                    descriptorTitle.visibility = View.VISIBLE
                    descriptorLayout.visibility = View.VISIBLE
                }
                //获取存储描述信息的布局
                val descriptorLayout:LinearLayout = view.findViewById(R.id.descriptorLayout)
                for (descriptor in descriptors) {
                    val descriptorView = LayoutInflater.from(mContext).inflate(
                        R.layout.bleclient_list_item_descriptor,descriptorLayout,false)
                    //描述uuid
                    val descriptorUUID:TextView = descriptorView.findViewById(com.daqi.bleaction.R.id.descriptorUUID)
                    //描述读取按钮
                    val readDescriptorBtn:ImageView = descriptorView.findViewById(com.daqi.bleaction.R.id.readBtn)
                    //描述写入按钮
                    val writeDescriptorBtn:ImageView = descriptorView.findViewById(com.daqi.bleaction.R.id.writeBtn)
                    //设置描述值
                    val descriptorValue:TextView = descriptorView.findViewById(R.id.descriptorValue)

                    //设置描述uuid
                    descriptorUUID.text = descriptor.uuid.toString()
                    //无法判断描述的权限，只能全部显示。设置只读权限的描述，nRF也全部显示的（即显示写入和读取按钮）。
                    //设置读取按钮监听
                    readDescriptorBtn.setOnClickListener {
                        bleConnectionHelper.readDescriptor(descriptor,descriptorValue)
                    }
                    //设置写入按钮监听
                    writeDescriptorBtn.setOnClickListener {
                        showWriteDialog(descriptor)
                    }
                    descriptorLayout.addView(descriptorView)
                }
            }
            //将当前特征具体的布局添加到特征容器布局中
            characteristicLayout.addView(view)
        }

        /**
          * 特征值写入弹窗
          */
        private fun showWriteDialog(characteristic:BluetoothGattCharacteristic){
            //弹窗视图
            val view = LayoutInflater.from(itemView.context)
                .inflate(com.daqi.bleaction.R.layout.bleclient_dialog,null)
            //输入框
            val valueEditText:EditText = view.findViewById(R.id.valueEditText)
            //确认按钮
            val positiveBtn:TextView = view.findViewById(R.id.positiveBtn)

            //展示dialog
            val dialog = AlertDialog.Builder(mContext)
                .setView(view)
                .show()
            //设置确认按钮监听
            positiveBtn.setOnClickListener {
                val dataStr = valueEditText.text.toString()
                //判断是否为十六进制字符串
                if (Pattern.matches("^[A-Fa-f0-9]+\$", dataStr)){
                    //将十六进制字符串转换为字节数组发送给外设
                    bleConnectionHelper.writeCharacteristic(characteristic,hexStringToBytes(dataStr))
                    //顺便关闭弹窗
                    dialog.dismiss()
                }else{
                    ToastUtils.showBottomToast(mContext,"$dataStr 不是十六进制")
                }
            }
        }

        /**
          * 描述值写入弹窗
          */
        private fun showWriteDialog(descriptor: BluetoothGattDescriptor){
            //弹窗视图
            val view = LayoutInflater.from(itemView.context)
                .inflate(com.daqi.bleaction.R.layout.bleclient_dialog,null)
            //输入框
            val valueEditText:EditText = view.findViewById(R.id.valueEditText)
            //确认按钮
            val positiveBtn:TextView = view.findViewById(R.id.positiveBtn)
            //展示dialog
            val dialog = AlertDialog.Builder(mContext)
                .setView(view)
                .show()
            //设置确认按钮监听
            positiveBtn.setOnClickListener {
                val dataStr = valueEditText.text.toString()
                //判断是否为十六进制字符串
                if (Pattern.matches("^[A-Fa-f0-9]+\$", dataStr)){
                    //将十六进制字符串转换为字节数组发送给外设
                    bleConnectionHelper.writeDescriptor(descriptor,hexStringToBytes(dataStr))
                    //顺便关闭弹窗
                    dialog.dismiss()
                }else{
                    ToastUtils.showBottomToast(mContext,"$dataStr 不是十六进制")
                }
            }
        }

        /**
          * 获取具体属性
          */
        private fun getProperties(properties:Int) :String{
            val buffer = StringBuffer()
            for (i in 1..8){
                when(i){
                    1 -> if (properties and PROPERTY_BROADCAST != 0)
                             buffer.append("BROADCAST,")
                    2 -> if (properties and PROPERTY_READ != 0)
                            buffer.append("READ,")
                    3 -> if (properties and PROPERTY_WRITE_NO_RESPONSE != 0)
                            buffer.append("WRITE NO RESPONSE,")
                    4 -> if (properties and PROPERTY_WRITE != 0)
                            buffer.append("WRITE,")
                    5 -> if (properties and PROPERTY_NOTIFY != 0 )
                            buffer.append("NOTIFY,")
                    6 -> if (properties and PROPERTY_INDICATE != 0)
                            buffer.append("INDICATE,")
                    7 -> if (properties and PROPERTY_SIGNED_WRITE != 0)
                            buffer.append("SIGNED WRITE,")
                    8 -> if (properties and PROPERTY_EXTENDED_PROPS != 0)
                            buffer.append("EXTENDED PROPS,")
                }
            }
            val str = buffer.toString()
            if (str.length > 0)
            //减最后的逗号
                return str.substring(0,str.length - 1)
            else
                return ""
        }
    }
}