package com.mogilogi.objecttotemplate

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.HashMap

class ObjectToTemplate
{
    companion object
    {
        private val gson = Gson()

         fun applyTemplate(template: String, obj: Any, extraFields: HashMap<String, Any> = HashMap<String, Any>()): String {
            var result = template
            val fields = obj::class.java.declaredFields
            val objectName = obj::class.simpleName

            for (field in fields)
            {
                field.isAccessible = true
                val newValue = field.get(obj).toString()
                val oldValue = "{${objectName}.${field.name}}"

                if(isJson(newValue))
                {
                    val fieldsInJson = gson.fromJson<Map<String, String>>(newValue, object : TypeToken<Map<String, String>>() {}.type)
                    for (jsonField in fieldsInJson)
                    {
                        val jsonOldValue = "{${objectName}.${field.name}!${jsonField.key}}"
                        val jsonNewValue = jsonField.value

                        result = result.replace(jsonOldValue, jsonNewValue)
                    }
                    val pattern = "\\[[^}]+\\:\\{$objectName\\.${field.name}!.[^}]+\\}\\]".toRegex()
                    result = result.replace(pattern, "")

                    val regx = "\\{$objectName\\.${field.name}![^}]+\\}".toRegex()
                    result = result.replace(regx, "")
                }
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

            val listPattern = "#[^}]+\\#".toRegex()
            val fieldPattern = "\\{[^}]+\\}".toRegex()
            result = listPattern.replace(result, "")
            result = fieldPattern.replace(result, "")

            result = result.replace("[", "").replace("]", "")

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
}