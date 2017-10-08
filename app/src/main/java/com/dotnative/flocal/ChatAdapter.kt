@file:Suppress("UNCHECKED_CAST", "ClassName", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import de.hdodenhof.circleimageview.CircleImageView

class ChatAdapter(val context: Context, val messages: MutableList<Chat>, val profilePicURL: Uri?,
                  val chatID: String, val displayProgress: Boolean): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // MARK: - Vars

    private var chatAdapterListener: ChatAdapterListener = context as MainActivity
    val misc = Misc()
    val myID = misc.setMyID(context)

    // MARK: - Navigation

    interface ChatAdapterListener {
        fun showImageFromChat(uri: Uri, chatID: String, messageID: String)
        fun showVideoFromChat(uri: Uri, preview: Uri?, chatID: String, messageID: String)
    }

    // MARK: - Recycler View

    override fun getItemCount(): Int {
        if (messages.isEmpty()) {
            return 1
        }

        val size = messages.size
        if (displayProgress) {
            return size + 1
        }
        return size
    }

    override fun getItemViewType(position: Int): Int {
        if (messages.isEmpty()) {
            return 4
        }

        if (displayProgress && (position == messages.size)) {
            return 5
        }

        val message = messages[position]
        val userID = message.userID
        val type = message.type
        if (userID != myID) {
            if (type == "text") {
                return 0
            } else {
                return 1
            }
        } else {
            if (type == "text") {
                return 2
            } else {
                return 3
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent?.context)
        when (viewType) {
            0 -> {
                val view: View = layoutInflater.inflate(R.layout.card_chat, parent, false)
                return chatViewHolder(view)
            }
            1 -> {
                val view: View = layoutInflater.inflate(R.layout.card_chat_image, parent, false)
                return chatImageViewHolder(view)
            }
            2 -> {
                val view: View = layoutInflater.inflate(R.layout.card_my_chat, parent, false)
                return myChatViewHolder(view)
            }
            3 -> {
                val view: View = layoutInflater.inflate(R.layout.card_my_chat_image, parent, false)
                return myChatImageViewHolder(view)
            }
            5 -> {
                val view: View = layoutInflater.inflate(R.layout.card_progress_bar, parent, false)
                return progressBarViewHolder(view)
            }
            else -> {
                val view: View = layoutInflater.inflate(R.layout.card_no_content, parent, false)
                return noContentViewHolder(view)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        when (holder?.itemViewType) {
            0 -> {
                val viewHolder: chatViewHolder = holder as chatViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val message = messages[position]

                val profilePicImageView = viewHolder.profilePicImageView
                Picasso.with(context).load(profilePicURL).into(profilePicImageView)

                val chatTextView = viewHolder.chatTextView
                val text = message.message
                chatTextView.text = text

                val timestampTextView = viewHolder.timestampTextView
                val timestamp = message.timestamp
                val originalTimestamp = message.originalTimestamp
                if (position > 0) {
                    val lastOriginalTimestamp = messages[position - 1].originalTimestamp
                    val delay = lastOriginalTimestamp + 300
                    if (originalTimestamp > delay) {
                        timestampTextView.layoutParams.height = R.dimen.chatTimestampHeight
                        timestampTextView.text = timestamp
                    } else {
                        timestampTextView.layoutParams.height = 0

                    }
                } else {
                    timestampTextView.layoutParams.height = R.dimen.chatTimestampHeight
                    timestampTextView.text = timestamp
                }
            }

            1 -> {
                val viewHolder: chatImageViewHolder = holder as chatImageViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val message = messages[position]
                val messageID = message.messageID

                val profilePicImageView = viewHolder.profilePicImageView
                Picasso.with(context).load(profilePicURL).into(profilePicImageView)

                val imageView = viewHolder.imageView
                val playImageView = viewHolder.playImageView
                val type = message.type
                if (type == "image") {
                    playImageView.visibility = ImageView.INVISIBLE
                    val chatPicURL = message.chatPicURL
                    if (chatPicURL != null) {
                        Picasso.with(context).load(chatPicURL).into( object: Target {
                            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                                if (bitmap != null) {
                                    imageView.setImageBitmap(bitmap)
                                    misc.setImageAspect(imageView, bitmap)
                                }
                            }
                            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                            override fun onBitmapFailed(errorDrawable: Drawable?) {}
                        })
                        card.setOnClickListener { chatAdapterListener.showImageFromChat(chatPicURL, chatID, messageID) }
                    }
                }
                if (type == "video") {
                    playImageView.visibility = ImageView.VISIBLE
                    val chatVidPreviewURL = message.chatVidPreviewURL
                    if (chatVidPreviewURL != null) {
                        Picasso.with(context).load(chatVidPreviewURL).into( object: Target {
                            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                                if (bitmap != null) {
                                    imageView.setImageBitmap(bitmap)
                                    misc.setImageAspect(imageView, bitmap)
                                }
                            }
                            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                            override fun onBitmapFailed(errorDrawable: Drawable?) {}
                        })
                    }
                    val chatVidURL = message.chatVidURL
                    if (chatVidURL != null) {
                        card.setOnClickListener { chatAdapterListener.showVideoFromChat(chatVidURL, chatVidPreviewURL, chatID, messageID) }
                    }
                }

                val timestampTextView = viewHolder.timestampTextView
                val timestamp = message.timestamp
                val originalTimestamp = message.originalTimestamp
                if (position > 0) {
                    val lastOriginalTimestamp = messages[position - 1].originalTimestamp
                    val delay = lastOriginalTimestamp + 300
                    if (originalTimestamp > delay) {
                        timestampTextView.layoutParams.height = R.dimen.chatTimestampHeight
                        timestampTextView.text = timestamp
                    } else {
                        timestampTextView.layoutParams.height = 0

                    }
                } else {
                    timestampTextView.layoutParams.height = R.dimen.chatTimestampHeight
                    timestampTextView.text = timestamp
                }
            }

            2 -> {
                val viewHolder: myChatViewHolder = holder as myChatViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val message = messages[position]

                val chatTextView = viewHolder.chatTextView
                val text = message.message
                chatTextView.text = text

                val timestampTextView = viewHolder.timestampTextView
                val timestamp = message.timestamp
                val originalTimestamp = message.originalTimestamp
                if (position > 0) {
                    val lastOriginalTimestamp = messages[position - 1].originalTimestamp
                    val delay = lastOriginalTimestamp + 300
                    if (originalTimestamp > delay) {
                        timestampTextView.layoutParams.height = R.dimen.chatTimestampHeight
                        timestampTextView.text = timestamp
                    } else {
                        timestampTextView.layoutParams.height = 0

                    }
                } else {
                    timestampTextView.layoutParams.height = R.dimen.chatTimestampHeight
                    timestampTextView.text = timestamp
                }
            }

            3 -> {
                val viewHolder: myChatImageViewHolder = holder as myChatImageViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val message = messages[position]
                val messageID = message.messageID

                val imageView = viewHolder.imageView
                val playImageView = viewHolder.playImageView
                val type = message.type
                if (type == "image") {
                    playImageView.visibility = ImageView.INVISIBLE
                    val chatPicURL = message.chatPicURL
                    if (chatPicURL != null) {
                        Picasso.with(context).load(chatPicURL).into( object: Target {
                            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                                if (bitmap != null) {
                                    imageView.setImageBitmap(bitmap)
                                    misc.setImageAspect(imageView, bitmap)
                                }
                            }
                            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                            override fun onBitmapFailed(errorDrawable: Drawable?) {}
                        })
                        card.setOnClickListener { chatAdapterListener.showImageFromChat(chatPicURL, chatID, messageID) }
                    }
                }
                if (type == "video") {
                    playImageView.visibility = ImageView.VISIBLE
                    val chatVidPreviewURL = message.chatVidPreviewURL
                    if (chatVidPreviewURL != null) {
                        Picasso.with(context).load(chatVidPreviewURL).into( object: Target {
                            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                                if (bitmap != null) {
                                    imageView.setImageBitmap(bitmap)
                                    misc.setImageAspect(imageView, bitmap)
                                }
                            }
                            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                            override fun onBitmapFailed(errorDrawable: Drawable?) {}
                        })
                    }
                    val chatVidURL = message.chatVidURL
                    if (chatVidURL != null) {
                        card.setOnClickListener { chatAdapterListener.showVideoFromChat(chatVidURL, chatVidPreviewURL, chatID, messageID) }
                    }
                }

                val timestampTextView = viewHolder.timestampTextView
                val timestamp = message.timestamp
                val originalTimestamp = message.originalTimestamp
                if (position > 0) {
                    val lastOriginalTimestamp = messages[position - 1].originalTimestamp
                    val delay = lastOriginalTimestamp + 300
                    if (originalTimestamp > delay) {
                        timestampTextView.layoutParams.height = R.dimen.chatTimestampHeight
                        timestampTextView.text = timestamp
                    } else {
                        timestampTextView.layoutParams.height = 0

                    }
                } else {
                    timestampTextView.layoutParams.height = R.dimen.chatTimestampHeight
                    timestampTextView.text = timestamp
                }
            }

            5 -> {
                val viewHolder: progressBarViewHolder = holder as progressBarViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val progressBar = viewHolder.progressBar
                progressBar.progressDrawable.setColorFilter(Color.parseColor("#009688"), PorterDuff.Mode.SRC_IN)
            }

            else -> {
                val viewHolder: noContentViewHolder = holder as noContentViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)

                val textView = viewHolder.noContentTextView
                textView.setText(R.string.noChat)
            }
        }
    }

    // MARK: - View Holders

    class chatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.chatCardView)
        var profilePicImageView: CircleImageView = itemView.findViewById(R.id.profilePicImageView)
        var chatTextView: TextView = itemView.findViewById(R.id.chatTextView)
        var timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
    }

    class chatImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.chatImageCardView)
        var profilePicImageView: CircleImageView = itemView.findViewById(R.id.profilePicImageView)
        var imageView: ImageView = itemView.findViewById(R.id.imageView)
        var timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        var playImageView: ImageView = itemView.findViewById(R.id.playImageView)
    }

    class myChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.myChatCardView)
        var chatTextView: TextView = itemView.findViewById(R.id.chatTextView)
        var timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
    }

    class myChatImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.myChatImageCardView)
        var imageView: ImageView = itemView.findViewById(R.id.imageView)
        var timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        var playImageView: ImageView = itemView.findViewById(R.id.playImageView)
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
