package com.hfast.fakenotification;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Carl on 2014-08-02.
 */
public class MessageAdapter extends BaseAdapter {

    private Activity mContext;
    private List<String> mList;
    private LayoutInflater mLayoutInflater = null;
    private String[] messages;

    public MessageAdapter(Activity context, List<String> messageList) {
        mContext = context;
        mList = messageList;
        mLayoutInflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        //this.messages = messages;
    }

    @Override
    public int getCount() {
        return mList.size();
    }

    @Override
    public Object getItem(int pos) {
        return mList.get(pos);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View v = convertView;
        ViewHolder viewHolder;
        if (convertView == null) {
            LayoutInflater li = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = li.inflate(R.layout.message_display_layout, null);
            viewHolder = new ViewHolder(v);
            v.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) v.getTag();
        }

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) viewHolder.message.getLayoutParams();
        String message = mList.get(position);
        if(message.startsWith("Self:")){
            message = message.substring(5);
            lp.gravity = Gravity.RIGHT;
            viewHolder.message.setBackgroundResource(R.drawable.rounded_corner2);

        }else{
            lp.gravity = Gravity.LEFT;
            viewHolder.message.setBackgroundResource(R.drawable.rounded_corner7);
        }

        viewHolder.message.setText(message);

        return v;

    }

    class ViewHolder {
        public TextView message;

        public ViewHolder(View base) {
            message = (TextView) base.findViewById(R.id.incoming_textview);
        }
    }
}
