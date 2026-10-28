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
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(StudioDesign.background(this@SupportInboxActivity)) }
        val header=StudioDesign.toolbar(this,"Support inbox") { finish() }
        header.addView(StudioDesign.button(this,"Refresh") { refresh() }.apply { contentDescription="Refresh support inbox" },LinearLayout.LayoutParams(-2,Ui.dp(this,48)))
        root.addView(header)
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(Ui.dp(context,16),Ui.dp(context,8),Ui.dp(context,16),Ui.dp(context,16)) }
        body.addView(StudioDesign.label(this,"Your requests and replies, all in one place.",14f,secondary=true))
        body.addView(StudioDesign.button(this,"＋ New request",primary=true) { ContactUs.show(this) { refresh() } },LinearLayout.LayoutParams(-1,Ui.dp(this,52)).apply { topMargin=Ui.dp(this@SupportInboxActivity,16) })
        status=StudioDesign.label(this,"",13f,secondary=true);body.addView(status,LinearLayout.LayoutParams(-1,-2).apply { topMargin=Ui.dp(this@SupportInboxActivity,12);bottomMargin=Ui.dp(this@SupportInboxActivity,12) })
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        body.addView(ScrollView(this).apply { addView(list) },LinearLayout.LayoutParams(-1,0,1f))
        body.addView(StudioDesign.button(this,"Delete online data") { deleteOnlineData() }.apply { isEnabled=AdminConnection.configured },LinearLayout.LayoutParams(-1,Ui.dp(this,48)).apply { topMargin=Ui.dp(this@SupportInboxActivity,12) })
        body.addView(StudioDesign.label(this,"Removes online analytics and support history. Local projects stay on this device.",12f,secondary=true))
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);refresh()
    }
    private fun deleteOnlineData() {
        AppDialog(this).setTitle("Delete online data?").setMessage("Remove this installation's analytics and support conversations from the admin server. Local projects, photos and videos remain on this phone.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ ->
                AdminConnection.deleteOnlineData(this) { result ->
                    if(isDestroyed || isFinishing) return@deleteOnlineData
                    result.fold(onSuccess={ refresh();Toast.makeText(this,"Online data deleted.",Toast.LENGTH_LONG).show() },onFailure={ status.text=it.message ?: "Unable to delete online data." })
                }
            }.show()
    }
    private fun refresh() {
        if(!AdminConnection.configured) {
            status.text=if(BuildConfig.POLICY_PLACEHOLDER) "Live support is unavailable in this test edition." else "Online conversations are unavailable. Use New request to contact us by email."
            list.removeAllViews()
            list.addView(StudioDesign.label(this,"Support inbox",20f,bold=true).apply { gravity=Gravity.CENTER;setPadding(0,Ui.dp(context,32),0,Ui.dp(context,12)) })
            list.addView(StudioDesign.label(this,"Your conversations will appear here when online support is available.",14f,secondary=true).apply { gravity=Gravity.CENTER })
            return
        }
        status.text="Loading support messages…"
        AdminConnection.inbox(this) { result ->
            if(isDestroyed || isFinishing)return@inbox
            result.fold(onSuccess={ rows ->
                list.removeAllViews()
                status.text=if(rows.length()==0) "" else "${rows.length()} support requests"
                if(rows.length()==0) {
                    val empty=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(Ui.dp(context,24),Ui.dp(context,40),Ui.dp(context,24),Ui.dp(context,40));background=StudioDesign.shape(context,StudioDesign.raised(context),18) }
                    empty.addView(ImageView(this).apply { setImageResource(R.drawable.ph_envelope);imageTintList=android.content.res.ColorStateList.valueOf(StudioDesign.accent(context));importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)))
                    empty.addView(StudioDesign.label(this,"No support requests yet",20f,bold=true).apply { gravity=Gravity.CENTER;setPadding(0,Ui.dp(context,16),0,Ui.dp(context,8)) })
                    empty.addView(StudioDesign.label(this,"Need a hand? Submit a request and read our replies here.",14f,secondary=true).apply { gravity=Gravity.CENTER })
                    list.addView(empty,LinearLayout.LayoutParams(-1,-2))
                }
                for(i in 0 until rows.length()) {
                    val ticket=rows.getJSONObject(i)
                    val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(Ui.dp(context,16),Ui.dp(context,16),Ui.dp(context,16),Ui.dp(context,16));background=StudioDesign.shape(context,StudioDesign.surface(context),16,true);isFocusable=true;setOnClickListener { openThread(ticket.getString("id")) } }
                    val state=when(ticket.optString("status").lowercase()) { "resolved","closed" -> "Resolved";"in_progress","in progress","pending","replied" -> "In progress";else -> "Open" }
                    card.addView(StudioDesign.label(this,state,12f,bold=true).apply { setTextColor(StudioDesign.accent(context));background=StudioDesign.shape(context,StudioDesign.raised(context),8);setPadding(Ui.dp(context,10),Ui.dp(context,5),Ui.dp(context,10),Ui.dp(context,5)) },LinearLayout.LayoutParams(-2,-2))
                    card.addView(StudioDesign.label(this,ticket.optString("subject","Support request"),17f,bold=true).apply { setPadding(0,Ui.dp(context,12),0,Ui.dp(context,6)) })
                    card.addView(StudioDesign.label(this,"View conversation  →",13f,secondary=true))
                    list.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=Ui.dp(this@SupportInboxActivity,12) })
                }
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
            val incoming=message.getString("author")=="admin"
            val bubble=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(Ui.dp(context,14),Ui.dp(context,12),Ui.dp(context,14),Ui.dp(context,12));background=StudioDesign.shape(context,if(incoming) StudioDesign.surface(context) else StudioDesign.raised(context),14,true) }
            bubble.addView(StudioDesign.label(this,if(incoming) "Support team" else "You",12f,bold=true))
            bubble.addView(StudioDesign.label(this,message.getString("body"),15f).apply { setTextIsSelectable(true);setPadding(0,Ui.dp(context,6),0,0) })
            body.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=Ui.dp(this@SupportInboxActivity,12);if(incoming) marginEnd=Ui.dp(this@SupportInboxActivity,24) else marginStart=Ui.dp(this@SupportInboxActivity,24) })
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
