@file:Suppress("DEPRECATION", "ClassName", "UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

class NotificationsAdapter(context: Context, val notifications: MutableList<NotificationClass>, val displayProgress: Boolean):
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // MARK: - Vars

    private var notificationsAdapterListener: NotificationsAdapterListener = context as MainActivity
    val misc = Misc()

    // MARK: - Navigation

    interface NotificationsAdapterListener {
        fun turnToFragmentFromNotifications(type: String, postID: String, userID: String, handle: String)
    }

    // MARK: - Recycler View

    override fun getItemCount(): Int {
        if (notifications.isEmpty()) {
            return 1
        }

        val size = notifications.size
        if (displayProgress) {
            return size + 1
        }
        return size
    }

    override fun getItemViewType(position: Int): Int {
        if (notifications.isEmpty()) {
            return 1
        }

        if (displayProgress && (position == notifications.size)) {
            return 2
        }

        return 0
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent?.context)
        when (viewType) {
            1 -> {
                val view: View = layoutInflater.inflate(R.layout.card_no_content, parent, false)
                return noContentViewHolder(view)
            }
            2 -> {
                val view: View = layoutInflater.inflate(R.layout.card_progress_bar, parent, false)
                return progressBarViewHolder(view)
            }
            else -> {
                val view: View = layoutInflater.inflate(R.layout.notifications_card, parent, false)
                return notificationsViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        when (holder?.itemViewType) {
            1 -> {
                val viewHolder: noContentViewHolder = holder as noContentViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)

                val textView = viewHolder.noContentTextView
                textView.setText(R.string.noNotifications)
            }
            2 -> {
                val viewHolder: progressBarViewHolder = holder as progressBarViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val progressBar = viewHolder.progressBar
                progressBar.progressDrawable.setColorFilter(Color.parseColor("#006064"), PorterDuff.Mode.SRC_IN)
            }
            else -> {
                val viewHolder: notificationsViewHolder = holder as notificationsViewHolder
                val notification = notifications[position]

                val timestamp = notification.timestamp
                val message = notification.message
                val text = "$message $timestamp"
                val coloredText = misc.stringWithColoredTags(text, timestamp)
                val textView = viewHolder.notificationsTextView
                if (Build.VERSION.SDK_INT >= 24) {
                    textView.text = Html.fromHtml(coloredText, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    textView.text = Html.fromHtml(coloredText)
                }

                val type = notification.type
                val card = viewHolder.card
                val imageView = viewHolder.notificationsImageView
                when (type) {
                    "upvote" -> {
                        card.setCardBackgroundColor(misc.flocalOrangeFade)
                        imageView.setImageResource(R.drawable.upvote_s)
                    }
                    "reply" -> {
                        card.setCardBackgroundColor(misc.flocalYellowFade)
                        imageView.setImageResource(R.drawable.reply_s)
                    }
                    "tagged" -> {
                        card.setCardBackgroundColor(misc.flocalYellowFade)
                        imageView.setImageResource(R.drawable.tagged_s)
                    }
                    "chat" -> {
                        card.setCardBackgroundColor(misc.flocalTealFade)
                        imageView.setImageResource(R.drawable.chat_s)
                    }
                    "added" -> {
                        card.setCardBackgroundColor(misc.flocalGreenFade)
                        imageView.setImageResource(R.drawable.add_s)
                    }
                    else -> {
                        card.setCardBackgroundColor(Color.WHITE)
//                        imageView.setImageResource(R.drawable.app_ion)
                    }
                }

                val postID = notification.postID
                val userID = notification.userID
                val handle = notification.handle
                card.setOnClickListener {
                    notificationsAdapterListener.turnToFragmentFromNotifications(type, postID, userID, handle)
                }

            }
        }
    }

    // MARK: - View Holders

    class notificationsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.notificationsCardView)
        var notificationsImageView: ImageView = itemView.findViewById(R.id.notificationsImageView)
        var notificationsTextView: TextView = itemView.findViewById(R.id.notificationsTextView)
    }

    class noContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.noContentCardView)
        var noContentTextView: TextView = itemView.findViewById(R.id.noContentTextView)
    }

    class progressBarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.progressBarCardView)
        var progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    }

}
