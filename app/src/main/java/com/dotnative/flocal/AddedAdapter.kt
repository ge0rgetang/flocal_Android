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
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class AddedAdapter(val context: Context, val frag: AddedFragment, val locals: MutableList<User>,
                   val added: MutableList<User>, val followers: MutableList<User>,
                   val selectedSegment: String, val displayProgress: Boolean): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // MARK: - Vars

    private var addedAdapterListener: AddedAdapterListener = context as MainActivity
    val misc = Misc()
    val myID = misc.setMyID(context)

    // MARK: - Navigation

    interface AddedAdapterListener {
        fun turnToUserProfileFromAdded(userID: String, handle: String, chatID: String)
    }

    // MARK: - Recycler View

    override fun getItemCount(): Int {
        val peeps = determinePeeps()

        if (peeps.isEmpty()) {
            return 1
        }

        val size = peeps.size
        if (displayProgress) {
            return size + 1
        }
        return size
    }

    override fun getItemViewType(position: Int): Int {
        val peeps = determinePeeps()
        if (peeps.isEmpty()) {
            return 2
        }

        if (displayProgress && (position == peeps.size)) {
            return 3
        }

        when (selectedSegment) {
            "locals", "followers" -> {
                return 0
            }
            "added" -> {
                return 1
            }
            else -> {
                return 2
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent?.context)
        when (viewType) {
            0 -> {
                val view: View = layoutInflater.inflate(R.layout.card_user_list_button, parent, false)
                return userListButtonViewHolder(view)
            }
            1 -> {
                val view: View = layoutInflater.inflate(R.layout.card_user_list, parent, false)
                return userListViewHolder(view)
            }
            3 -> {
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
        val peeps = determinePeeps()

        when (holder?.itemViewType) {
            0 -> {
                val viewHolder: userListButtonViewHolder = holder as userListButtonViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val user = peeps[position]

                val handleTextView = viewHolder.handleTextView
                val handle = user.handle
                handleTextView.text = "@$handle"

                val profilePicImageView = viewHolder.profilePicImageView
                val profilePicURL = user.profilePicURL
                if (profilePicURL != null) {
                    Picasso.with(context).load(profilePicURL).into(profilePicImageView)
                } else {
                    profilePicImageView.setImageResource(misc.setDefaultPic(handle))
                }

                val infoTextView = viewHolder.infoTextView
                val points = user.points
                val pointsFormatted = misc.setCount(points)
                val followers = user.followersCount
                val followersFormatted = misc.setCount(followers)
                val infoString = "$pointsFormatted points / $followersFormatted followers"

                val description = user.description
                val descriptionLower = description.toLowerCase()
                if (descriptionLower == "no description set" || descriptionLower == "tap to set description") {
                    infoTextView.text = infoString
                } else {
                    infoTextView.text = infoString + "\n" + description
                }

                val userID = user.userID
                val didIAdd = user.didIAdd
                val addImageView = viewHolder.addImageView
                if (myID == userID) {
                    addImageView.setImageResource(R.drawable.smiley_s)
                } else if (didIAdd) {
                    addImageView.setImageResource(R.drawable.check_mark_s)
                } else {
                    addImageView.setImageResource(R.drawable.add_s)
                    addImageView.setOnClickListener { frag.addUser(position, userID) }
                }

                val chatID = misc.setChatID(myID, userID)
                card.setOnClickListener { addedAdapterListener.turnToUserProfileFromAdded(userID, handle, chatID) }
            }

            1 -> {
                val viewHolder: userListViewHolder = holder as userListViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val user = peeps[position]

                val handleTextView = viewHolder.handleTextView
                val handle = user.handle
                handleTextView.text = "@$handle"

                val profilePicImageView = viewHolder.profilePicImageView
                val profilePicURL = user.profilePicURL
                if (profilePicURL != null) {
                    Picasso.with(context).load(profilePicURL).into(profilePicImageView)
                } else {
                    profilePicImageView.setImageResource(misc.setDefaultPic(handle))
                }

                val infoTextView = viewHolder.infoTextView
                val points = user.points
                val pointsFormatted = misc.setCount(points)
                val followers = user.followersCount
                val followersFormatted = misc.setCount(followers)
                val infoString = "$pointsFormatted points / $followersFormatted followers"

                val description = user.description
                val descriptionLower = description.toLowerCase()
                if (descriptionLower == "no description set" || descriptionLower == "tap to set description") {
                    infoTextView.text = infoString
                } else {
                    infoTextView.text = infoString + "\n" + description
                }

                val userID = user.userID
                val chatID = misc.setChatID(myID, userID)
                card.setOnClickListener { addedAdapterListener.turnToUserProfileFromAdded(userID, handle, chatID) }
            }

            3 -> {
                val viewHolder: progressBarViewHolder = holder as progressBarViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val progressBar = viewHolder.progressBar
                progressBar.progressDrawable.setColorFilter(Color.parseColor("#4CAF50"), PorterDuff.Mode.SRC_IN)
            }

            else -> {
                val viewHolder: noContentViewHolder = holder as noContentViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)

                val textView = viewHolder.noContentTextView
                textView.text = getNoContentMessage()
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

    class userListButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.userListButtonCardView)
        var profilePicImageView: CircleImageView = itemView.findViewById(R.id.profilePicImageView)
        var handleTextView: TextView = itemView.findViewById(R.id.handleTextView)
        var infoTextView: TextView = itemView.findViewById(R.id.infoTextView)
        var addImageView: ImageView = itemView.findViewById(R.id.addImageView)
    }

    class noContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.noContentCardView)
        var noContentTextView: TextView = itemView.findViewById(R.id.noContentTextView)
    }

    class progressBarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.progressBarCardView)
        var progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    }

    // MARK - Misc

    fun determinePeeps(): MutableList<User> {
        when (selectedSegment) {
            "locals" -> { return locals }
            "added" -> { return added }
            "followers" -> { return followers }
            else -> { return followers }
        }
    }

    fun getNoContentMessage(): String {
        when (selectedSegment) {
            "locals" -> {
                return "No locals found. Locals with the most followers show here."
            }
            "added" -> {
                return "You don't have anyone added yet. Posts from added people show up in the last section of your home page." +
                        " Search for people to add in the side menu or add some popular people in the locals section below."
            }
            "followers" -> {
                return "No one has added you yet. Think of your followers as your audience. Posts you make will show up in their home page." +
                        " Tell your friends to add you!"
            }
            else -> {
                return "error"
            }
        }
    }

}