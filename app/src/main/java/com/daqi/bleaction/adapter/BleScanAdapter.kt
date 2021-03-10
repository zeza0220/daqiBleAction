package com.daqi.bleaction.adapter

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.daqi.bleaction.*
import com.daqi.bleaction.bean.BleDevice
import android.content.ClipboardManager
import android.content.ClipData
import com.daqi.bleaction.bean.ADStructure


/**
 * 扫描RecyclerView的设配器
 * @author daqi
 * @date 2019/6/8
 */
class BleScanAdapter(val context: Context): RecyclerView.Adapter<BleScanAdapter.BleViewHolder>(){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleViewHolder {
        var view = LayoutInflater.from(context).inflate(
            R.layout.blescan_list_item,parent,false)
        return BleViewHolder(view)
    }

    override fun getItemCount(): Int {
        return mBleDeviceList.size
    }

    override fun onBindViewHolder(holder: BleViewHolder, position: Int) {
        //绑定蓝牙数据
        holder.bindBleDate(mBleDeviceList[position])
    }

    /**
     * 扫描RecyclerView的ViewHolder
     * @author daqi
     * @date 2019/6/8
     */
    class BleViewHolder(view: View):RecyclerView.ViewHolder(view){
        private lateinit var mContext: Context

        //mac地址
        private lateinit var macAddress: TextView
        private lateinit var name: TextView
        private lateinit var rssi: TextView
        private lateinit var bondState: TextView
        private lateinit var rawBtn: TextView
        private lateinit var connecBtn: Button
        private lateinit var otherLayout: LinearLayout
        private lateinit var bleImg:ImageView
        private lateinit var uuid16Layout:LinearLayout
        private lateinit var uuid16Text: TextView
        private lateinit var uuid32Layout:LinearLayout
        private lateinit var uuid32Text: TextView
        private lateinit var manufacturerDataLayout:LinearLayout
        private lateinit var manufacturerDataText: TextView
        private lateinit var serviceDataLayout:LinearLayout
        private lateinit var serviceDataText: TextView

        //存储广播数据单元数组
        private val mADStructureArray = ArrayList<ADStructure>()

        init {
            mContext = itemView.context
            macAddress = itemView.findViewById(R.id.macAddressText)
            name = itemView.findViewById(R.id.nameText)
            rssi = itemView.findViewById(R.id.rssiText)
            bondState = itemView.findViewById(R.id.bondStateText)
            rawBtn = itemView.findViewById(R.id.rawDataBtn)
            connecBtn = itemView.findViewById(R.id.connecBtn)
            otherLayout = itemView.findViewById(R.id.otherLayout)
            bleImg = itemView.findViewById(R.id.bleImg)
            uuid16Layout = itemView.findViewById(R.id.uuid16Layout)
            uuid16Text = itemView.findViewById(R.id.uuid16Text)
            uuid32Layout = itemView.findViewById(R.id.uuid32Layout)
            uuid32Text = itemView.findViewById(R.id.uuid32Text)
            manufacturerDataLayout = itemView.findViewById(R.id.manufacturerDataLayout)
            manufacturerDataText = itemView.findViewById(R.id.manufacturerDataText)
            serviceDataLayout = itemView.findViewById(R.id.serviceDataLayout)
            serviceDataText = itemView.findViewById(R.id.serviceDataText)
        }

        fun bindBleDate(bleDevice: BleDevice){
            //设置mac地址
            macAddress.text = bleDevice.device.address
            //设置设备名称
            name.text =  bleDevice.device.name ?: "N/A"
            //设置信号值
            rssi.text = bleDevice.rssi.toString()
            //绑定状态
            when(bleDevice.device.bondState){
                10 ->
                    bondState.text = "Not BONDED"
                12 ->
                    bondState.text = "BONDED"
                else ->
                    bondState.text = "Not BONDED"
            }
            //判断是否可以连接
            if (!bleDevice.isConnectable){
                connecBtn.visibility = View.INVISIBLE
            }else{
                connecBtn.visibility = View.VISIBLE
            }
            //判断厂商类型
            if (bleDevice.scanRecord != null){
                //判断是否是苹果的厂商id
                if (bleDevice.scanRecord?.getManufacturerSpecificData(0x4C) != null){
                    bleImg.setImageDrawable(mContext.getDrawable(R.drawable.apple))
                }else if (bleDevice.scanRecord?.getManufacturerSpecificData(0x06) != null){
                    //判断是否是微软的厂商id
                    bleImg.setImageDrawable(mContext.getDrawable(R.drawable.windows))
                }else{
                    bleImg.setImageDrawable(mContext.getDrawable(R.drawable.bluetoothon))
                }
            }else{
                bleImg.setImageDrawable(mContext.getDrawable(R.drawable.bluetoothon))
            }

            //解析蓝牙广播数据报文
            val rawData = parseBleADData(bleDevice.scanRecordBytes)
            //初始化隐藏布局
            initOtherLayout(bleDevice);
            //设置监听
            initListener(bleDevice.device.address,rawData,bleDevice.device.name ?: "N/A")
        }

        /**
          * 初始化隐藏布局
          */
        private fun initOtherLayout(bleDevice:BleDevice){
            //先隐藏，并移除之前的布局
            otherLayout.visibility = View.GONE
            bleDevice.scanRecord?.let{ scanRecord ->
                //隐藏
                uuid16Layout.visibility = View.GONE
                uuid32Layout.visibility = View.GONE
                //UUID
                scanRecord.serviceUuids?.let {
                    //添加UUID信息
                    for (adStructure in mADStructureArray) {
                        when(adStructure.type){
                            //完整的16bit UUID 列表
                            "0x03" -> {
                                uuid16Text.text = ""
                                //除去之前添加的0x
                                val dataStr = adStructure.data.substring(2,adStructure.data.length)
                                for (i in 0 until dataStr.length/4){
                                    val uuid = "0x" + dataStr.substring(2 + i * 4,4 + i * 4) +
                                            dataStr.substring(0 + i * 4,2 + i * 4)
                                    if (uuid16Text.text.equals("")){
                                        uuid16Text.text = uuid
                                    }else{
                                        uuid16Text.text = uuid16Text.text.toString() + "," + uuid
                                    }
                                }
                                uuid16Layout.visibility = View.VISIBLE
                            }
                            //完整的32bit UUID 列表
                            "0x05" -> {
                                uuid32Text.text = ""
                                //除去之前添加的0x
                                val dataStr = adStructure.data.substring(2,adStructure.data.length)
                                for (i in 0 until dataStr.length/8){
                                    val uuid = "0x" + dataStr.substring(6 + i * 8,8 + i * 8) + dataStr.substring(4 + i * 8,6 + i * 8) +
                                            dataStr.substring(2 + i * 8,4 + i * 8) + dataStr.substring(0 + i * 8,2 + i * 8)
                                    if (uuid32Text.text.equals("")){
                                        uuid32Text.text = uuid
                                    }else{
                                        uuid32Text.text = uuid32Text.text.toString() + ", " + uuid
                                    }
                                }
                                uuid32Layout.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                //隐藏
                manufacturerDataLayout.visibility = View.GONE
                //厂商数据
                scanRecord.manufacturerSpecificData?.let{
                    //添加厂商数据信息
                    for (adStructure in mADStructureArray) {
                        if (adStructure.type.equals("0xFF")){
                            //除去之前添加的0x
                            val data = adStructure.data.subSequence(2,adStructure.data.length)
                            //获取厂商id
                            val manufacturerId = data.substring(2,4) + data.substring(0,2)
                            //获取真正的厂商数据
                            val manufacturerData = data.substring(4,data.length)
                            manufacturerDataText.text = "厂商ID: ${"0x" + manufacturerId} \n数据: ${"0x" + manufacturerData}"
                            manufacturerDataLayout.visibility = View.VISIBLE
                            break
                        }
                    }
                }
                //隐藏
                serviceDataLayout.visibility = View.GONE
                //服务数据
                scanRecord.serviceData?.let{
                    serviceDataText.text = ""
                    //添加厂商数据信息
                    for (adStructure in mADStructureArray) {
                        when(adStructure.type){
                            //16-bit 服务数据
                            "0x16" -> {
                                //除去之前添加的0x
                                val data = adStructure.data.subSequence(2,adStructure.data.length)
                                //获取16bit的uuid
                                val uuid = "0x" + data.substring(2,4) + data.substring(0,2)
                                //获取对应的数据
                                val serviceData = data.substring(4,data.length)
                                serviceDataText.text = "16-bit UUID: ${"0x" + uuid} \n数据: ${"0x" + serviceData}"
                                serviceDataLayout.visibility = View.VISIBLE
                            }
                            //android发不出32bit的服务数据
                            //32-bit 服务数据
                            "0x20" -> {

                            }
                        }
                    }
                }
            }
            //设置厂商数据
        }

        /**
          * 设置点击监听
          */
        private fun initListener(macAddress: String,rawDataStr:String,name:String){
            //连接按钮点击监听
            connecBtn.setOnClickListener {
                mContext.startActivity(BleClientPageActivity.newIntent(mContext,macAddress,name))
            }
            //原始数据按钮点击监听
            rawBtn.setOnClickListener {
                showDialog(rawDataStr)
            }
            //列表item点击监听
            itemView.setOnClickListener {
                if (otherLayout.visibility == View.GONE){
                    otherLayout.visibility = View.VISIBLE
                }else{
                    otherLayout.visibility = View.GONE
                }
            }
        }

        /**
          * 统计数据单元，展示dialog
          */
        private fun showDialog(rawDataStr:String){
            val view = LayoutInflater.from(itemView.context)
                .inflate(R.layout.blescan_dialog,null)
            val mTableLayout:TableLayout = view.findViewById(R.id.mTableLayout)
            val rawData:TextView = view.findViewById(R.id.rawDataText)
            val positiveBtn:TextView = view.findViewById(R.id.positiveBtn)
            //显示原始数据
            rawData.text = "0x" + rawDataStr.toUpperCase()
            //设置数据单元
            for (adStructure in mADStructureArray) {
                setBleADStructureTable(adStructure.length,adStructure.type,adStructure.data,mTableLayout)
            }
            //展示dialog
            val dialog = AlertDialog.Builder(mContext)
                .setView(view)
                .show()

            //设置点击事件
            positiveBtn.setOnClickListener {
                dialog.dismiss()
            }
            //原始数据点击事件
            rawData.setOnClickListener {
                val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("daqi",rawData.text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(mContext,"复制成功",Toast.LENGTH_SHORT).show()
            }
        }

        /**
          * 解析蓝牙广播报文，获取数据单元
          */
        private fun parseBleADData(byteArray: ByteArray):String{
            //将字节数组转十六进制字符串
            var rawDataStr = BluetoothUtils.bytesToHexString(byteArray)
            //清空之前解析的数据单元
            mADStructureArray.clear()
            //存储实际数据段
            var dataStr:String = ""
            while (true){
                //取长度
                var lengthStr = rawDataStr.substring(0,2)
                //如果长度为0，则退出
                if (lengthStr.equals("00")){
                    break
                }
                //将长度转10进制
                val length = Integer.parseInt(lengthStr,16)
                //length表示后面多少字节也属于该数据单元，所以整个数据单元的长度 = length + 1
                val data = rawDataStr.substring(0,(length + 1) * 2 )
                //存储每个数据单元的值
                dataStr += data
                //裁剪原始数据，方便后面裁剪数据单元
                rawDataStr = rawDataStr.substring((length + 1) * 2,rawDataStr.length)
                //创建广播数据单元bean，并存储到数据中
                //第一个字节是长度，第二个字节是类型，再后面才是数据（一个字节用两个十六进制字符串表示）
                mADStructureArray.add(ADStructure(length,"0x" + data.substring(2,4).toUpperCase()
                    ,"0x" + data.substring(4,data.length).toUpperCase()))
            }
            //返回蓝牙广播数据报文
            return dataStr
        }

        /**
          * 设置广播数据单元
          */
        private fun setBleADStructureTable(length:Int,type:String,data:String,parent: ViewGroup){
            //创建表格
            val tableRow: TableRow = TableRow(mContext)
            //创建length视图
            val lengthView = TextView(mContext)
            lengthView.layoutParams = TableRow.LayoutParams(1,ViewGroup.LayoutParams.WRAP_CONTENT)
            lengthView.setPadding(dp2px(4f).toInt())
            lengthView.text = length.toString()
            lengthView.gravity = Gravity.CENTER
            tableRow.addView(lengthView)
            //创建Type视图
            val typeView = TextView(mContext)
            typeView.layoutParams = TableRow.LayoutParams(1,ViewGroup.LayoutParams.WRAP_CONTENT)
            typeView.setPadding(dp2px(4f).toInt())
            typeView.text = type
            typeView.gravity = Gravity.CENTER
            tableRow.addView(typeView)
            //创建Value视图
            val valueView = TextView(mContext)
            val valueLayoutParams = TableRow.LayoutParams(1,ViewGroup.LayoutParams.WRAP_CONTENT)
            valueLayoutParams.span = 3
            valueView.layoutParams = valueLayoutParams
            valueView.setPadding(dp2px(4f).toInt())
            valueView.text = data
            valueView.gravity = Gravity.CENTER
            tableRow.addView(valueView)
            parent.addView(tableRow)
        }

        private fun dp2px(num:Float) :Float{
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,num,itemView.context.resources.displayMetrics)
        }
    }
}