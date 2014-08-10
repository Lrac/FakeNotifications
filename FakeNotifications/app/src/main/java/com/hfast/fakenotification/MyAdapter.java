package com.hfast.fakenotification;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Carl on 2014-07-29.
 */
public class MyAdapter extends BaseAdapter{
    private Activity mContext;
    private List<TextMessage> mList;
    private LayoutInflater mLayoutInflater = null;
    private String[] messages;

    public MyAdapter(Activity context, List<TextMessage> textList) {
        mContext = context;
        mList = textList;
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
        CompleteListViewHolder viewHolder;
        if(convertView == null){
            LayoutInflater li = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = li.inflate(R.layout.inbox_item_layout, null);
            viewHolder = new CompleteListViewHolder(v);
            v.setTag(viewHolder);
        } else{
            viewHolder = (CompleteListViewHolder) v.getTag();
        }

        TextMessage textMessage = mList.get(position);

        viewHolder.sender.setText(textMessage.getSender());
        viewHolder.preview.setText(textMessage.getRecentMessage());
        viewHolder.date.setText(textMessage.getDate());

        return v;

    }
}
class CompleteListViewHolder {
    public TextView sender;
    public TextView preview;
    public TextView date;
    public CompleteListViewHolder(View base) {
        sender = (TextView) base.findViewById(R.id.name);
        preview = (TextView) base.findViewById(R.id.preview);
        date = (TextView) base.findViewById(R.id.date);
    }
}
