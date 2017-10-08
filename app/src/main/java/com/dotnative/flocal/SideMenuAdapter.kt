@file:Suppress("MemberVisibilityCanPrivate", "ClassName")


package com.dotnative.flocal

import android.annotation.SuppressLint
import android.support.v7.widget.CardView
import android.content.Context
import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso

class SideMenuAdapter(val context: Context, val isSearchActive: Boolean, val searchResults: MutableList<User>):
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // MARK: - Vars

    private var sideMenuAdapterListener: SideMenuAdapterListener = context as MainActivity
    val misc = Misc()
    val myID = misc.setMyID(context)
    val selectedIndex = misc.getSideMenuIndex(context)

    // MARK: - Navigation

    interface SideMenuAdapterListener {
        fun turnToFragmentFromSideMenu(name: String)
        fun addUserFromSideMenu(index: Int, userID: String)
        fun logoutFromSideMenu()
    }

    // MARK: - Recycler View

    override fun getItemCount(): Int {
        if (myID == "0") {
            return 2
        }

        if (isSearchActive) {
            if (searchResults.isEmpty()) {
                return 1
            }
            return searchResults.size
        }

        return 10
    }

    override fun getItemViewType(position: Int): Int {
        if (myID == "0") {
            return 0
        }

        if (isSearchActive) {
            if (searchResults.isEmpty()) {
                return 4
            }
            return 3
        }

        if ((position <= 3) || (position in 5..7)) {
            return 0
        }

        if (position == 9) {
            return 1
        }

        return 2
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent?.context)
        when (viewType) {
            1 -> {
                val view: View = layoutInflater.inflate(R.layout.card_menu_button, parent, false)
                return menuButtonViewHolder(view)
            }
            2 -> {
                val view: View = layoutInflater.inflate(R.layout.card_spacer, parent, false)
                return spacerViewHolder(view)
            }
            3 -> {
                val view: View = layoutInflater.inflate(R.layout.card_search, parent, false)
                return searchViewHolder(view)
            }
            4 -> {
                val view: View = layoutInflater.inflate(R.layout.card_no_content, parent, false)
                return noContentViewHolder(view)
            }
            else -> {
                val view: View = layoutInflater.inflate(R.layout.card_side_menu, parent, false)
                return sideMenuViewHolder(view)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        when (holder?.itemViewType) {
            1 -> {
                val viewHolder: menuButtonViewHolder = holder as menuButtonViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                viewHolder.menuButton.setOnClickListener { sideMenuAdapterListener.logoutFromSideMenu() }
            }
            2 -> {
                val viewHolder: spacerViewHolder = holder as spacerViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
            }
            3 -> {
                val viewHolder: searchViewHolder = holder as searchViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val result = searchResults[position]

                val handle = result.handle
                val handleTextView = viewHolder.handleTextView
                handleTextView.text = "@$handle"

                val profilePicURL = result.profilePicURL
                val profilePicImageView = viewHolder.profilePicImageView
                if (profilePicURL != null) {
                    Picasso.with(context).load(profilePicURL).into(profilePicImageView)
                } else {
                    profilePicImageView.setImageResource(misc.setDefaultPic(handle))
                }

                val userID = result.userID
                val didIAdd = result.didIAdd
                val addImageView = viewHolder.addImageView
                if (didIAdd) {
                    addImageView.setImageResource(R.drawable.check_mark_s)
                } else {
                    addImageView.setImageResource(R.drawable.add_large)
                    addImageView.setOnClickListener { sideMenuAdapterListener.addUserFromSideMenu(position, userID) }
                }
            }
            4 -> {
                val viewHolder: noContentViewHolder = holder as noContentViewHolder
                val card = viewHolder.card
                card.setCardBackgroundColor(Color.WHITE)
                val textView = viewHolder.noContentTextView
                textView.setText(R.string.noSearchResults)
            }
            else -> {
                val viewHolder: sideMenuViewHolder = holder as sideMenuViewHolder
                val card = viewHolder.card

                if (myID == "0") {
                    when (position) {
                        0 -> {
                            viewHolder.menuTextView.setText(R.string.signUp)
                            if (selectedIndex == position) {
                                viewHolder.menuImageView.setImageResource(R.drawable.sign_up_s)
                                card.setCardBackgroundColor(misc.flocalFade)
                            } else {
                                viewHolder.menuImageView.setImageResource(R.drawable.sign_up)
                                card.setCardBackgroundColor(Color.WHITE)
                            }
                            card.setOnClickListener { sideMenuAdapterListener.turnToFragmentFromSideMenu("Sign Up") }
                        }
                        else -> {
                            viewHolder.menuTextView.setText(R.string.login)
                            if (selectedIndex == position) {
                                viewHolder.menuImageView.setImageResource(R.drawable.login_s)
                                card.setCardBackgroundColor(misc.flocalFade)
                            } else {
                                viewHolder.menuImageView.setImageResource(R.drawable.login)
                                card.setCardBackgroundColor(Color.WHITE)
                            }
                            card.setOnClickListener { sideMenuAdapterListener.turnToFragmentFromSideMenu("Login") }
                        }
                    }

                } else {
                    when (position) {
                        1 -> {
                            viewHolder.menuTextView.setText(R.string.peeps)
                            if (selectedIndex == position) {
                                viewHolder.menuImageView.setImageResource(R.drawable.added_s)
                                card.setCardBackgroundColor(misc.flocalGreenFade)
                            } else {
                                viewHolder.menuImageView.setImageResource(R.drawable.added)
                                card.setCardBackgroundColor(Color.WHITE)
                            }
                            card.setOnClickListener { sideMenuAdapterListener.turnToFragmentFromSideMenu("Added") }
                        }
                        2 -> {
                            viewHolder.menuTextView.setText(R.string.chats)
                            if (selectedIndex == position) {
                                viewHolder.menuImageView.setImageResource(R.drawable.chats_s)
                                card.setCardBackgroundColor(misc.flocalTealFade)
                            } else {
                                viewHolder.menuImageView.setImageResource(R.drawable.chats)
                                card.setCardBackgroundColor(Color.WHITE)
                            }
                            card.setOnClickListener { sideMenuAdapterListener.turnToFragmentFromSideMenu("Chat List") }
                        }
                        3 -> {
                            viewHolder.menuTextView.setText(R.string.me)
                            if (selectedIndex == position) {
                                viewHolder.menuImageView.setImageResource(R.drawable.me_blue)
                                card.setCardBackgroundColor(misc.flocalBlueFade)
                            } else {
                                viewHolder.menuImageView.setImageResource(R.drawable.me)
                                card.setCardBackgroundColor(Color.WHITE)
                            }
                            card.setOnClickListener { sideMenuAdapterListener.turnToFragmentFromSideMenu("Me") }
                        }
                        5 -> {
                            viewHolder.menuTextView.setText(R.string.reportBug)
                            if (selectedIndex == position) {
                                viewHolder.menuImageView.setImageResource(R.drawable.report_bug_s)
                                card.setCardBackgroundColor(misc.flocalBlueGreyFade)
                            } else {
                                viewHolder.menuImageView.setImageResource(R.drawable.report_bug)
                                card.setCardBackgroundColor(Color.WHITE)
                            }
                            card.setOnClickListener { sideMenuAdapterListener.turnToFragmentFromSideMenu("Report Bug") }
                        }
                        6 -> {
                            viewHolder.menuTextView.setText(R.string.feedback)
                            if (selectedIndex == position) {
                                viewHolder.menuImageView.setImageResource(R.drawable.feedback_s)
                                card.setCardBackgroundColor(misc.flocalRedFade)
                            } else {
                                viewHolder.menuImageView.setImageResource(R.drawable.feedback)
                                card.setCardBackgroundColor(Color.WHITE)
                            }
                            card.setOnClickListener { sideMenuAdapterListener.turnToFragmentFromSideMenu("Feedback")}
                        }
                        7 -> {
                            viewHolder.menuTextView.setText(R.string.about)
                            if (selectedIndex == position) {
                                viewHolder.menuImageView.setImageResource(R.drawable.about_s)
                                card.setCardBackgroundColor(misc.flocalPurpleFade)
                            } else {
                                viewHolder.menuImageView.setImageResource(R.drawable.about)
                                card.setCardBackgroundColor(Color.WHITE)
                            }
                            card.setOnClickListener { sideMenuAdapterListener.turnToFragmentFromSideMenu("About") }
                        }
                        else -> {
                            viewHolder.menuTextView.setText(R.string.home)
                            if (selectedIndex == position) {
                                viewHolder.menuImageView.setImageResource(R.drawable.home_s)
                                card.setCardBackgroundColor(misc.flocalFade)
                            } else {
                                viewHolder.menuImageView.setImageResource(R.drawable.home)
                                card.setCardBackgroundColor(Color.WHITE)
                            }
                            card.setOnClickListener { sideMenuAdapterListener.turnToFragmentFromSideMenu("Home") }
                        }
                    }
                }

            }
        }
    }

    // MARK: - View Holders

    class sideMenuViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.sideMenuCardView)
        var menuImageView: ImageView = itemView.findViewById(R.id.profilePicImageView)
        var menuTextView: TextView = itemView.findViewById(R.id.menuTextView)
    }

    class menuButtonViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.menuButtonCardView)
        var menuButton: Button = itemView.findViewById(R.id.menuButton)
    }

    class spacerViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.spacerCardView)
    }

    class searchViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.userSearchCardView)
        var profilePicImageView: ImageView = itemView.findViewById(R.id.profilePicImageView)
        var handleTextView: TextView = itemView.findViewById(R.id.handleTextView)
        var addImageView: ImageView = itemView.findViewById(R.id.addImageView)
    }

    class noContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var card: CardView = itemView.findViewById(R.id.noContentCardView)
        var noContentTextView: TextView = itemView.findViewById(R.id.noContentTextView)
    }
}