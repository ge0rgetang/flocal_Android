@file:Suppress("UNCHECKED_CAST", "ClassName", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class ChatListAdapter(val context: Context, val chatList: MutableList<Chat>, val displayProgress: Boolean):
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // MARK: - Vars

    private var chatListAdapterListener: ChatListAdapterListener = context as MainActivity
    val misc = Misc()
    val myID = misc.setMyID(context)

    // MARK: - Navigation

    interface ChatListAdapterListener {
        fun turnToChatFromChatList(chatID: String, userID: String, handle: String, type: String, messageID: String)
        fun turnToUserProfileFromChatList(userID: String, handle: String, chatID: String)
    }

    // MARK: - Recycler View

    override fun getItemCount(): Int {
        if (chatList.isEmpty()) {
            return 1
        }

        val size = chatList.size
        if (displayProgress) {
            return size + 1
        }
        return size
    }

    override fun getItemViewType(position: Int): Int {
        if (chatList.isEmpty()) {
            return 1
        }

        if (displayProgress && (position == chatList.size)) {
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
                val view: View = layoutInflater.inflate(R.layout.card_user_list, parent, false)
                return userListViewHolder(view)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        when (holder?.itemViewType) {
            1 -> {
                val viewHolder: noContentViewHolder = holder as noContentViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)

                val textView = viewHolder.noContentTextView
                textView.setText(R.string.noChatList)
            }
            2 -> {
                val viewHolder: progressBarViewHolder = holder as progressBarViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val progressBar = viewHolder.progressBar
                progressBar.progressDrawable.setColorFilter(Color.parseColor("#009688"), PorterDuff.Mode.SRC_IN)
            }
            else -> {
                val viewHolder: userListViewHolder = holder as userListViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val chat = chatList[position]

                val handleTextView = viewHolder.handleTextView
                val handle = chat.handle
                handleTextView.text = "@$handle"

                val profilePicImageView = viewHolder.profilePicImageView
                val profilePicURL = chat.profilePicURL
                if (profilePicURL != null) {
                    Picasso.with(context).load(profilePicURL).into(profilePicImageView)
                } else {
                    profilePicImageView.setImageResource(misc.setDefaultPic(handle))
                }

                val textView = viewHolder.infoTextView
                val userID = chat.userID
                if (userID == myID) {
                    textView.setTextColor(Color.LTGRAY)
                } else {
                    textView.setTextColor(Color.BLACK)
                }

                val type = chat.type
                val timestamp = chat.timestamp
                when (type) {
                    "image" -> {
                        textView.text = "picture sent $timestamp"
                    }
                    "video" -> {
                        textView.text = "video sent $timestamp"
                    }
                    else -> {
                        val message = chat.message
                        textView.text = "$message $timestamp"
                    }
                }

                val chatID = chat.chatID
                val messageID = chat.messageID
                card.setOnClickListener {
                    chatListAdapterListener.turnToChatFromChatList(chatID, userID, handle, type, messageID)
                }
                profilePicImageView.setOnClickListener {
                    chatListAdapterListener.turnToUserProfileFromChatList(userID, handle, chatID)
                }
            }
        }
    }

    // MARK: - View Holders

    class userListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.userListCardView)
        var profilePicImageView: CircleImageView = itemView.findViewById(R.id.profilePicImageView)
        var handleTextView: TextView = itemView.findViewById(R.id.handleTextView)
        var infoTextView: TextView = itemView.findViewById(R.id.infoTextView)
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

