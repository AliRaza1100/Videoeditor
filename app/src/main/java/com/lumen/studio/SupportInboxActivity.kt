package com.lumen.studio

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import org.json.JSONObject

class SupportInboxActivity: BaseActivity() {
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(StudioDesign.background(this@SupportInboxActivity));setPadding(Ui.dp(context,20),Ui.dp(context,16),Ui.dp(context,20),Ui.dp(context,16)) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(StudioDesign.button(this,"←") { finish() }.apply { contentDescription="Back" },LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)))
        header.addView(StudioDesign.label(this,"Support inbox",24f,bold=true),LinearLayout.LayoutParams(0,-2,1f));root.addView(header)
        root.addView(StudioDesign.label(this,"Text-only conversations with the app administrator. Messages sync when you open this screen. No SMS service or media upload is used.",14f,secondary=true))
        root.addView(StudioDesign.button(this,"Refresh") { refresh() })
        status=StudioDesign.label(this,"",14f,secondary=true);root.addView(status)
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) },LinearLayout.LayoutParams(-1,0,1f));setContentView(root);refresh()
    }
    private fun refresh() {
        status.text="Loading support messages…"
        AdminConnection.inbox(this) { result ->
            if(isDestroyed || isFinishing)return@inbox
            list.removeAllViews()
            result.fold(onSuccess={ rows ->
                status.text=if(rows.length()==0) "No conversations yet. Use Contact Us to send a message." else "${rows.length()} recent conversations"
                for(i in 0 until rows.length()) {val row=rows.getJSONObject(i);list.addView(StudioDesign.button(this,"${row.getString("subject")} · ${row.getString("status")}") { openThread(row.getString("id")) },LinearLayout.LayoutParams(-1,-2).apply { topMargin=Ui.dp(this@SupportInboxActivity,12) })}
            },onFailure={ status.text=it.message ?: "Unable to load support." })
        }
    }
    private fun openThread(id: String) {
        status.text="Loading conversation…"
        AdminConnection.thread(this,id) { result ->
            if(isDestroyed || isFinishing)return@thread
            result.fold(onSuccess={ conversation -> showConversation(id,conversation);status.text="" },onFailure={ status.text=it.message })
        }
    }
    private fun showConversation(id: String,conversation: JSONObject) {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(Ui.dp(context,20),Ui.dp(context,8),Ui.dp(context,20),Ui.dp(context,12)) }
        val messages=conversation.getJSONArray("messages")
        for(i in 0 until messages.length()) {
            val message=messages.getJSONObject(i)
            body.addView(StudioDesign.label(this,if(message.getString("author")=="admin") "Admin" else "You",14f,bold=true))
            body.addView(StudioDesign.label(this,message.getString("body"),15f).apply { setTextIsSelectable(true) },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=Ui.dp(this@SupportInboxActivity,16) })
        }
        val reply=EditText(this).apply {hint="Write a reply";minLines=3;maxLines=8;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(4000));setTextColor(StudioDesign.text(context));setHintTextColor(StudioDesign.muted(context))};body.addView(reply)
        val messageId=java.util.UUID.randomUUID().toString()
        val dialog=AppDialog(this).setTitle(conversation.getString("subject")).setView(ScrollView(this).apply {addView(body)}).setNegativeButton("Close",null).setPositiveButton("Send reply",null).create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text=reply.text.toString().trim()
            if(text.isEmpty()){reply.error="Write a reply";return@setOnClickListener}
            val send=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);send.isEnabled=false
            AdminConnection.reply(this,id,messageId,text) { result ->
                if(isDestroyed || !dialog.isShowing)return@reply
                send.isEnabled=true
                result.fold(onSuccess={dialog.dismiss();openThread(id)},onFailure={reply.error=it.message ?: "Reply was not sent. Try again."})
            }
        }
    }
}
