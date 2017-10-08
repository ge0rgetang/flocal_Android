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
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import de.hdodenhof.circleimageview.CircleImageView

class ReplyAdapter(val context: Context, val frag: ReplyFragment, val parentPost: Post, val orderedReplies: MutableList<Reply>,
                   val topReplies: MutableList<Reply>, val newestReplies: MutableList<Reply>, val selectedSegment: String,
                   val displayProgress: Boolean): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    // MARK: - Vars

    private var replyAdapterListener: ReplyAdapterListener = context as MainActivity
    val misc = Misc()
    val myID = misc.setMyID(context)
    val replies = determineReplies()

    // MARK: - Navigation

    interface ReplyAdapterListener {
        fun turnToUserProfileFromReply(userID: String?, handle: String)
        fun showImageFromReply(uri: Uri, postID: String)
        fun showVideoFromReply(uri: Uri, preview: Uri?, postID: String)
    }

    // MARK: - Recycler View

    override fun getItemCount(): Int {
        val size = replies.size

        if (replies.isEmpty()) {
            return 3
        }

        if (displayProgress) {
            return size + 3
        }

        return size + 2
    }

    override fun getItemViewType(position: Int): Int {
        if (displayProgress && position == replies.size + 2) {
            return 5
        }

        if ((replies.isEmpty() && position == 2) || (parentPost.postID == "0" && position == 0)) {
            return 4
        }

        when (position) {
            0 -> {
                val type = parentPost.type
                if (type == "text") {
                    return 0
                } else {
                    return 1
                }
            }
            1 -> {
                return 2
            }
            else -> {
                return 3
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent?.context)
        when (viewType) {
            0 -> {
                val view: View = layoutInflater.inflate(R.layout.card_post, parent, false)
                return postViewHolder(view)
            }
            1 -> {
                val view: View = layoutInflater.inflate(R.layout.card_post_image, parent, false)
                return postImageViewHolder(view)
            }
            2 -> {
                val view: View = layoutInflater.inflate(R.layout.card_write_reply, parent, false)
                return writeReplyViewHolder(view)
            }
            3 -> {
                val view: View = layoutInflater.inflate(R.layout.card_reply, parent, false)
                return replyViewHolder(view)
            }
            4 -> {
                val view: View = layoutInflater.inflate(R.layout.card_no_content, parent, false)
                return noContentViewHolder(view)
            }
            else -> {
                val view: View = layoutInflater.inflate(R.layout.card_progress_bar, parent, false)
                return progressBarViewHolder(view)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        when (holder?.itemViewType) {
            0 -> {
                val viewHolder: postViewHolder = holder as postViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val post = parentPost
                card.setOnClickListener { frag.showEditSheet(position) }

                val handleTextView = viewHolder.handleTextView
                val handle = post.handle
                handleTextView.text = "@$handle"

                val profilePicImageView = viewHolder.profilePicImageView
                val profilePicURL = post.profilePicURL
                if (profilePicURL != null) {
                    Picasso.with(context).load(profilePicURL).into(profilePicImageView)
                } else {
                    profilePicImageView.setImageResource(misc.setDefaultPic(handle))
                }
                val userID = post.userID
                profilePicImageView.setOnClickListener { replyAdapterListener.turnToUserProfileFromReply(userID, handle) }

                val contentTextView = viewHolder.textView
                val content = post.content
                contentTextView.text = getSpannable(content, position)

                val timestampTextView = viewHolder.timestampTextView
                val timestamp = post.timestamp
                timestampTextView.text = timestamp

                val replyTextView = viewHolder.replyTextView
                val replyString = post.replyString
                replyTextView.text = replyString

                val pointsTextView = viewHolder.pointsTextView
                val points = post.points
                val pointsFormatted = misc.setCount(points)
                pointsTextView.text = "$pointsFormatted points"

                val upvoteImageView = viewHolder.upvoteImageView
                val downvoteImageView = viewHolder.downvoteImageView
                val voteStatus = post.voteStatus
                when (voteStatus) {
                    "up" -> {
                        pointsTextView.setTextColor(misc.flocalOrange)
                        upvoteImageView.setImageResource(R.drawable.upvote_s)
                        downvoteImageView.setImageResource(R.drawable.downvote)
                    }
                    "down" -> {
                        pointsTextView.setTextColor(misc.flocalBlueGrey)
                        upvoteImageView.setImageResource(R.drawable.upvote)
                        downvoteImageView.setImageResource(R.drawable.downvote_s)
                    }
                    else -> {
                        pointsTextView.setTextColor(misc.flocalColor)
                        upvoteImageView.setImageResource(R.drawable.upvote)
                        downvoteImageView.setImageResource(R.drawable.downvote)
                    }
                }
                upvoteImageView.setOnClickListener { frag.upvote(position) }
                downvoteImageView.setOnClickListener { frag.downvote(position) }
            }

            1 -> {
                val viewHolder: postImageViewHolder = holder as postImageViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val post = parentPost
                card.setOnClickListener { frag.showEditSheet(position) }

                val imagePicImageView = viewHolder.imagePicImageView
                val playImageView = viewHolder.playImageView
                val type = post.type
                val postID = post.postID
                if (type == "image") {
                    playImageView.visibility = ImageView.INVISIBLE
                    val postPicURL = post.postPicURL
                    if (postPicURL != null) {
                        Picasso.with(context).load(postPicURL).into( object: Target {
                            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                                if (bitmap != null) {
                                    imagePicImageView.setImageBitmap(bitmap)
                                    misc.setImageAspect(imagePicImageView, bitmap)
                                }
                            }
                            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                            override fun onBitmapFailed(errorDrawable: Drawable?) {}
                        })
                        imagePicImageView.setOnClickListener { replyAdapterListener.showImageFromReply(postPicURL, postID) }
                    }
                } else {
                    playImageView.visibility = ImageView.VISIBLE
                    val postVidPreviewURL = post.postVidPreviewURL
                    if (postVidPreviewURL != null) {
                        Picasso.with(context).load(postVidPreviewURL).into( object: Target {
                            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                                if (bitmap != null) {
                                    imagePicImageView.setImageBitmap(bitmap)
                                    misc.setImageAspect(imagePicImageView, bitmap)
                                }
                            }
                            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                            override fun onBitmapFailed(errorDrawable: Drawable?) {}
                        })
                    }
                    val postVidURL = post.postVidURL
                    if (postVidURL != null) {
                        imagePicImageView.setOnClickListener { replyAdapterListener.showVideoFromReply(postVidURL, postVidPreviewURL, postID) }
                    }
                }

                val handleTextView = viewHolder.handleTextView
                val handle = post.handle
                handleTextView.text = "@$handle"

                val profilePicImageView = viewHolder.profilePicImageView
                val profilePicURL = post.profilePicURL
                if (profilePicURL != null) {
                    Picasso.with(context).load(profilePicURL).into(profilePicImageView)
                } else {
                    profilePicImageView.setImageResource(misc.setDefaultPic(handle))
                }
                val userID = post.userID
                profilePicImageView.setOnClickListener { replyAdapterListener.turnToUserProfileFromReply(userID, handle) }

                val contentTextView = viewHolder.textView
                val content = post.content
                contentTextView.text = getSpannable(content, position)

                val timestampTextView = viewHolder.timestampTextView
                val timestamp = post.timestamp
                timestampTextView.text = timestamp

                val replyTextView = viewHolder.replyTextView
                val replyString = post.replyString
                replyTextView.text = replyString

                val pointsTextView = viewHolder.pointsTextView
                val points = post.points
                val pointsFormatted = misc.setCount(points)
                pointsTextView.text = "$pointsFormatted points"

                val upvoteImageView = viewHolder.upvoteImageView
                val downvoteImageView = viewHolder.downvoteImageView
                val voteStatus = post.voteStatus
                when (voteStatus) {
                    "up" -> {
                        pointsTextView.setTextColor(misc.flocalOrange)
                        upvoteImageView.setImageResource(R.drawable.upvote_s)
                        downvoteImageView.setImageResource(R.drawable.downvote)
                    }
                    "down" -> {
                        pointsTextView.setTextColor(misc.flocalBlueGrey)
                        upvoteImageView.setImageResource(R.drawable.upvote)
                        downvoteImageView.setImageResource(R.drawable.downvote_s)
                    }
                    else -> {
                        pointsTextView.setTextColor(misc.flocalColor)
                        upvoteImageView.setImageResource(R.drawable.upvote)
                        downvoteImageView.setImageResource(R.drawable.downvote)
                    }
                }
                upvoteImageView.setOnClickListener { frag.upvote(position) }
                downvoteImageView.setOnClickListener { frag.downvote(position) }
            }

            2 -> {
                val viewHolder: writeReplyViewHolder = holder as writeReplyViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                card.setOnClickListener { frag.showWriteReply(null, position) }

                val editText = viewHolder.editText
                editText.isEnabled = false
                val sendImageView = viewHolder.sendImageView
                sendImageView.isEnabled = false
            }

            3 -> {
                val viewHolder: replyViewHolder = holder as replyViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val reply = replies[position - 2]
                card.setOnClickListener { frag.showEditSheet(position) }

                val handleTextView = viewHolder.handleTextView
                val handle = reply.handle
                val timestamp = reply.timestamp
                val handleText = "@$handle $timestamp"
                handleTextView.text = misc.stringWithColoredTags(handleText, timestamp)

                val profilePicImageView = viewHolder.profilePicImageView
                val profilePicURL = reply.profilePicURL
                if (profilePicURL != null) {
                    Picasso.with(context).load(profilePicURL).into(profilePicImageView)
                } else {
                    profilePicImageView.setImageResource(misc.setDefaultPic(handle))
                }
                val userID = reply.userID
                profilePicImageView.setOnClickListener { replyAdapterListener.turnToUserProfileFromReply(userID, handle) }

                val contentTextView = viewHolder.textView
                val content = reply.content
                contentTextView.text = getSpannable(content, position)

                val pointsTextView = viewHolder.pointsTextView
                val points = reply.points
                val pointsFormatted = misc.setCount(points)
                pointsTextView.text = "$pointsFormatted points"

                val upvoteImageView = viewHolder.upvoteImageView
                val downvoteImageView = viewHolder.downvoteImageView
                val voteStatus = reply.voteStatus
                when (voteStatus) {
                    "up" -> {
                        pointsTextView.setTextColor(misc.flocalOrange)
                        upvoteImageView.setImageResource(R.drawable.upvote_s)
                        downvoteImageView.setImageResource(R.drawable.downvote)
                    }
                    "down" -> {
                        pointsTextView.setTextColor(misc.flocalBlueGrey)
                        upvoteImageView.setImageResource(R.drawable.upvote)
                        downvoteImageView.setImageResource(R.drawable.downvote_s)
                    }
                    else -> {
                        pointsTextView.setTextColor(misc.flocalColor)
                        upvoteImageView.setImageResource(R.drawable.upvote)
                        downvoteImageView.setImageResource(R.drawable.downvote)
                    }
                }
                upvoteImageView.setOnClickListener { frag.upvote(position) }
                downvoteImageView.setOnClickListener { frag.downvote(position) }
            }

            4 -> {
                val viewHolder: noContentViewHolder = holder as noContentViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)

                val textView = viewHolder.noContentTextView
                if (position == 0) {
                    textView.setText(R.string.loading)
                } else {
                    textView.setText(R.string.noReplies)
                }
            }

            else -> {
                val viewHolder: progressBarViewHolder = holder as progressBarViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val progressBar = viewHolder.progressBar
                progressBar.progressDrawable.setColorFilter(Color.parseColor("#006064"), PorterDuff.Mode.SRC_IN)
            }
        }
    }

    // MARK: - View Holders

    class postViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.postCardView)
        var profilePicImageView: CircleImageView = itemView.findViewById(R.id.profilePicImageView)
        var handleTextView: TextView = itemView.findViewById(R.id.handleTextView)
        var textView: TextView = itemView.findViewById(R.id.textView)
        var timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        var replyTextView: TextView = itemView.findViewById(R.id.replyTextView)
        var pointsTextView: TextView = itemView.findViewById(R.id.pointsTextView)
        var upvoteImageView: ImageView = itemView.findViewById(R.id.upvoteImageView)
        var downvoteImageView: ImageView = itemView.findViewById(R.id.downvoteImageView)
    }

    class postImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.postImageCardView)
        var profilePicImageView: CircleImageView = itemView.findViewById(R.id.profilePicImageView)
        var handleTextView: TextView = itemView.findViewById(R.id.handleTextView)
        var textView: TextView = itemView.findViewById(R.id.textView)
        var timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        var replyTextView: TextView = itemView.findViewById(R.id.replyTextView)
        var pointsTextView: TextView = itemView.findViewById(R.id.pointsTextView)
        var upvoteImageView: ImageView = itemView.findViewById(R.id.upvoteImageView)
        var downvoteImageView: ImageView = itemView.findViewById(R.id.downvoteImageView)

        var imagePicImageView: ImageView = itemView.findViewById(R.id.imagePicImageView)
        var playImageView: ImageView = itemView.findViewById(R.id.playImageView)
    }

    class writeReplyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.writeReplyCardView)
        var editText: EditText = itemView.findViewById(R.id.editText)
        var sendImageView: ImageView = itemView.findViewById(R.id.sendImageView)
    }

    class replyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.replyCardView)
        var profilePicImageView: CircleImageView = itemView.findViewById(R.id.profilePicImageView)
        var handleTextView: TextView = itemView.findViewById(R.id.handleTextView)
        var textView: TextView = itemView.findViewById(R.id.textView)
        var pointsTextView: TextView = itemView.findViewById(R.id.pointsTextView)
        var upvoteImageView: ImageView = itemView.findViewById(R.id.upvoteImageView)
        var downvoteImageView: ImageView = itemView.findViewById(R.id.downvoteImageView)
    }

    class noContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.noContentCardView)
        var noContentTextView: TextView = itemView.findViewById(R.id.noContentTextView)
    }

    class progressBarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.progressBarCardView)
        var progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    }

    // MARK: - Navigation

    fun determineReplies(): MutableList<Reply> {
        when (selectedSegment) {
            "top" -> { return topReplies }
            "newest" -> { return newestReplies }
            else -> { return orderedReplies }
        }
    }

    fun getSpannable(text: String, position: Int): SpannableString {
        val spannableText = SpannableString(text)
        val stringArray = text.split(" ")

        val handlesToColor: MutableList<String> = mutableListOf()
        val otherText: MutableList<String> = mutableListOf()
        for (word in stringArray) {
            if (word.first().toString() == "@") {
                handlesToColor.add(word)
            } else {
                otherText.add(word)
            }
        }

        for (word in otherText) {
            var startIndex = text.indexOf(word) - 1
            if (startIndex < 0) {
                startIndex = 0
            }
            val endIndex = text.indexOf(word) + word.length
            val clickableSpan = object: ClickableSpan() {
                override fun onClick(p0: View?) {
                    frag.showEditSheet(position)
                }
                override fun updateDrawState(ds: TextPaint?) {
                    super.updateDrawState(ds)
                    ds?.isUnderlineText = false
                    ds?.color = Color.BLACK
                }
            }
            spannableText.setSpan(clickableSpan, startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        for (handle in handlesToColor) {
            val handleNoAt = handle.substring(1)
            val startIndex = text.indexOf(handle)
            val endIndex = startIndex + handle.length
            val clickableSpan = object: ClickableSpan() {
                override fun onClick(p0: View?) {
                    misc.doesUserExist(handleNoAt) { doesUserExist ->
                        if (doesUserExist) {
                            replyAdapterListener.turnToUserProfileFromReply(null, handleNoAt)
                        } else {
                            frag.alert("Handle not Found", "Either the tag is incorrect or we messed up.")
                        }
                    }
                }
                override fun updateDrawState(ds: TextPaint?) {
                    super.updateDrawState(ds)
                    ds?.isUnderlineText = false
                    ds?.color = misc.flocalColor
                }
            }
            spannableText.setSpan(clickableSpan, startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannableText
    }
}