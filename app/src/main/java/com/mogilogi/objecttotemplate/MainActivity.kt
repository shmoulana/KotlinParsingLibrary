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

        applyTemplate(textView.text.toString(), ticketObject, extraFields)
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





















    private fun applyTemplate(template: String, obj: Any, extraFields: HashMap<String, Any> = HashMap<String, Any>()): String {
        var result = template
        val fields = obj::class.java.declaredFields
        val objectName = obj::class.simpleName

        for (field in fields)
        {
            field.isAccessible = true
            val newValue = field.get(obj).toString()
            val oldValue = "{${objectName}.${field.name}}"

//            // is Date
//            if(oldValue.contains("Date") && field.type == Date::class.java)
//            {
//                newValue = SimpleDateFormat("dd/MM/yyyy").format(field.get(obj))
//
//                result = result.replace(oldValue, newValue)
//            }
            // is Json
            if(isJson(newValue))
            {
                val fieldsInJson = gson.fromJson<Map<String, String>>(newValue, object : TypeToken<Map<String, String>>() {}.type)
                for (jsonField in fieldsInJson)
                {
                    val jsonOldValue = "{${objectName}.${field.name}!${jsonField.key}}"
                    val jsonNewValue = jsonField.value

                    result = result.replace(jsonOldValue, jsonNewValue)
                }
                // if there is a field in template the dose not exists in Json, and the template line between [ ] line should be removed
                val pattern = "\\[[^}]+\\:\\{$objectName\\.${field.name}!.[^}]+\\}\\]".toRegex()
                result = result.replace(pattern, "")

                // if there is a field in template the dose not exists in Json, and the template line is not between [ ] should be cleared
                val regx = "\\{$objectName\\.${field.name}![^}]+\\}".toRegex()
                result = result.replace(regx, "")
            }
            // is List
            else if(field.type == List::class.java)
            {
                val searchString = "##${objectName}.${field.name}##"

                val templateLines = result.split("\n")
                val templateTitleIndex = templateLines.indexOf(searchString)
                var listTemplate = ""

                val nextLineIndex = templateTitleIndex + 1
                val indicesToRemove = ArrayList<Int>()

                if (templateTitleIndex != -1 && nextLineIndex < templateLines.size)
                {
                    indicesToRemove.add(templateTitleIndex)
                    indicesToRemove.add(nextLineIndex)

                    listTemplate = templateLines[nextLineIndex]

                    for (i in (nextLineIndex + 1) until templateLines.size)
                    {
                        if(templateLines[i].isEmpty()) { break }
                        listTemplate += "\n${templateLines[i]}"
                        indicesToRemove.add(i)
                    }
                }
                // remove the lines of the template so we don't confuse
                result = templateLines.filterIndexed { index, _ -> index !in indicesToRemove }.joinToString ("\n")

                val listOldValue = "#${objectName}.${field.name}#"
                var toBeReplacedString = ""
                var insertNewLine = false

                for(itemInList in field.get(obj) as List<*>)
                {
                    if (itemInList != null)
                    {
                        toBeReplacedString += "${if(insertNewLine) "\n" else "" /* "${listOldValue}\n" */ }${applyTemplate(listTemplate, itemInList)}"
                        insertNewLine = true
                    }
                }

                result = result.replace(listOldValue, toBeReplacedString)
            }
            else
            {
                val formatDecimalPattern = "\\[=FormatDecimal\\(\\{$objectName\\.${field.name}\\},(.*)\\)\\]".toRegex()
                val formatDecimalMatch = formatDecimalPattern.find(result)

                val formatDatePattern = "\\[=FormatDate\\(\\{$objectName\\.${field.name}\\},”(.*)”\\)\\]".toRegex()
                val formatDateMatch = formatDatePattern.find(result)

                if(formatDecimalMatch != null)
                {
                    val decimalPlaces = formatDecimalMatch.groups[1]?.value?.toIntOrNull()
                    if(decimalPlaces != null)
                    {
                        result = result.replace(formatDecimalMatch.value, formatDouble(newValue, decimalPlaces))
                    }
                }
                else if (formatDateMatch != null)
                {
                    val dateFormat = formatDateMatch.groups[1]?.value
                    result = result.replace(formatDateMatch.value, SimpleDateFormat(dateFormat).format(field.get(obj)))
                }
                else
                {
                    result = result.replace(oldValue, newValue)
                }
            }
        }

        result = setExtraFields(result, extraFields)

        // remove all template fields that were not filled
        val listPattern = "#[^}]+\\#".toRegex()
        val fieldPattern = "\\{[^}]+\\}".toRegex()
        result = listPattern.replace(result, "")
        result = fieldPattern.replace(result, "")

//         remove [ and ] from the removable template fields
        result = result.replace("[", "").replace("]", "")

        textView.setText(result)
        return result
    }
    private fun setExtraFields(template: String, extraFields: HashMap<String, Any> = HashMap<String, Any>()) : String {
        var result = template

        for (extraField in extraFields)
        {
            val newValue = extraField.value.toString()
            val oldValue = "{${extraField.key}}"

            val formatDecimalPattern = "\\[=FormatDecimal\\(\\{${extraField.key}\\},(.*)\\)\\]".toRegex()
            val formatDecimalMatch = formatDecimalPattern.find(result)

            val formatDatePattern = "\\[=FormatDate\\(\\{${extraField.key}\\},”(.*)”\\)\\]".toRegex()
            val formatDateMatch = formatDatePattern.find(result)

            if(formatDecimalMatch != null)
            {
                val decimalPlaces = formatDecimalMatch.groups[1]?.value?.toIntOrNull()
                if(decimalPlaces != null)
                {
                    result = result.replace(formatDecimalMatch.value, formatDouble(newValue, decimalPlaces))
                }
            }
            else if (formatDateMatch != null)
            {
                val dateFormat = formatDateMatch.groups[1]?.value
                result = result.replace(formatDateMatch.value, SimpleDateFormat(dateFormat).format(extraField.value))
            }
            else
            {
                result = result.replace(oldValue, newValue)
            }
        }

        return result
    }
    private fun formatDouble(value: String, decimalPlaces: Int): String {
        return "%.${decimalPlaces}f".format(value.toDouble())
    }
    private fun isJson(jsonString: String): Boolean {
        return try
        {
            val json = JsonParser().parse(jsonString)
            return json.isJsonObject || json.isJsonArray
        }
        catch (e: Exception) {
            false
        }
    }
}





