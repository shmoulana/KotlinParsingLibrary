package com.mogilogi.objecttotemplate

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity()
{
    val gson = Gson()
    val template = "Terminal\t: {Ticket.Terminal}\n" + "Cashier\t\t:  {LoginUser}\n" + "Date        :   [=FormatDate({Date},”dd/MM/yy”)] {Time}\n" + "Bill \t\t: [=FormatDate({Ticket.PaymentDate},”dd/MM/yyyy”)] {Ticket.PaymentTime}\n" + "[Cover:{Ticket.Tag!Pax}]\n" + "\n" + "#Ticket.Orders#\n" + "#Ticket.Discounts#\n" + "#Ticket.Services#\n" + "#Ticket.Taxes#\n" + "#Ticket.Payments#\n" + "\n" + "##Ticket.Payments##\n" + "Tendered : {Payment.Name}\n" + "Change   : {Payment.Tendered}\n" + "RefNo    : {Payment.PaymentInformation!RefNo}\n" + "\n" + "\n" + "##Ticket.Orders##\n" + "Name {Order.Name} [=FormatDecimal({Order.Quantity},2)] [=FormatDecimal({Order.Price},2)]\n" + "#Order.OrderTags#\n" + "##Order.OrderTags##\n" + "TagName [=FormatDate({OrderTags.Name},”dd/MM/yy”)]"

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView.setText(template)
    }

    fun onClickedApply(view: View)
    {
        val extraFields = HashMap<String, Any>()
        extraFields["LoginUser"] = "MyLoginUser"
        extraFields["Date"] = Calendar.getInstance().time
        extraFields["Time"] = "12:22PM"

        val payments = List<Payment>(2) { Payment("Dollar", "12", "{\"RefTime\":\"2020/10/10\"}") }

        val orderTags = List<OrderTags>(2) { OrderTags(Calendar.getInstance().time)}
        val orders = List<Order>(2) { Order("Burger", "2.354", "10", orderTags) }

        val ticketObject = Ticket(
            Terminal = "Terminal Name",
            PaymentDate = Calendar.getInstance().time,
            PaymentType = "12:00",
            Tag = "{\"Pax\":\"100\",\"PaxTime\":\"2020/10/10\"}",
//            "{\"PaxTime\":\"2020/10/10\"}",
            Payments = payments,
            Orders = orders
        )

        val fixedTemplate = ObjectToTemplate.applyTemplate(textView.text.toString(), ticketObject, extraFields)
       textView.setText(fixedTemplate)
    }

    fun onClickedReset(view: View)
    {
        textView.setText(template)
    }

    data class Ticket (
        var Terminal: String,
        var PaymentDate: Date,
        var PaymentType: String,
        var Tag: String,
        var Payments: List<Payment>,
        var Orders: List<Order>
    )

    data class Payment(
        var Name: String,
        var Tendered: String,
        var PaymentInformation: String
    )

    data class Order(
        var Name: String,
        var Quantity: String,
        var Price: String,
        var OrderTags: List<OrderTags>
    )

    data class OrderTags (
        var Name: Date
    )


}





