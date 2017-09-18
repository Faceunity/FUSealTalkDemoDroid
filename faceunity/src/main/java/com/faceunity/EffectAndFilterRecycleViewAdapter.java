package com.faceunity;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.faceunity.faceunitylibrary.R;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 特效选择RecycleView Adapter
 * 负责显示对应ICON，点击事件交互
 * Created by lirui on 2016/10/19.
 */

class EffectAndFilterRecycleViewAdapter extends RecyclerView.Adapter<EffectAndFilterRecycleViewAdapter.ItemViewHolder>{

    private static final int[] EFFECT_ITEM_RES_ARRAY = {
            R.drawable.ic_delete_all, R.drawable.lixiaolong, R.drawable.chibi_reimu, R.drawable.liudehua, R.drawable.yuguan, R.drawable.mood, R.drawable.gradient
    };

    private static final int[] FILTER_ITEM_RES_ARRAY = {R.drawable.nature, R.drawable.delta,
            R.drawable.electric, R.drawable.slowlived, R.drawable.tokyo, R.drawable.warm};

    public static final int RECYCLEVIEW_TYPE_EFFECT = 0;
    public static final int RECYCLEVIEW_TYPE_FILTER = 1;
    private int mRecycleViewType;

    private ArrayList<Boolean> mItemClickStateList; //store the click state in a list, restore it in time
    private RecyclerView mRecycleView;
    private int mLastClickPosition;

    public EffectAndFilterRecycleViewAdapter(RecyclerView recyclerView, int recycleViewType) {
        super();

        mRecycleView = recyclerView;
        mRecycleViewType = recycleViewType;

        mItemClickStateList = new ArrayList<>();
        initClickStateList();
    }

    @Override
    public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ItemViewHolder(new EffectAndFilterItemView(parent.getContext(), mRecycleViewType));
    }

    @Override
    public void onBindViewHolder(final ItemViewHolder holder, final int position) {
        //decide the border by click state
        if (mItemClickStateList.get(position) == null || !mItemClickStateList.get(position)) {
            holder.mItemView.setBackgroundUnSelected();
        } else {
            holder.mItemView.setBackgroundSelected();
        }
        //deal the resource image to present
        if (mRecycleViewType == RECYCLEVIEW_TYPE_EFFECT) {
            holder.mItemView.mItemIcon.setImageResource(EFFECT_ITEM_RES_ARRAY[
                    position % EFFECT_ITEM_RES_ARRAY.length]);
        } else {
            holder.mItemView.mItemText.setVisibility(View.VISIBLE);
            holder.mItemView.mItemIcon.setImageResource(FILTER_ITEM_RES_ARRAY[
                    position % FILTER_ITEM_RES_ARRAY.length]);
            holder.mItemView.mItemText.setText(FUManager.FILTERS[
                    position % FILTER_ITEM_RES_ARRAY.length].toUpperCase());
        }

        holder.mItemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!FUManager.creatingItem) {
                    if (mOnItemSelectedListener != null) {
                        mOnItemSelectedListener.onItemSelected(position, mRecycleViewType);
                    }
                    if (mLastClickPosition != position) {
                        ItemViewHolder lastClickItemViewHolder = (ItemViewHolder) mRecycleView
                                .findViewHolderForAdapterPosition(mLastClickPosition);
                        //restore the image background of last click position
                        if (lastClickItemViewHolder != null) {
                            lastClickItemViewHolder.mItemView.setBackgroundUnSelected();
                        }
                        mItemClickStateList.set(mLastClickPosition, false);
                    }
                    holder.mItemView.setBackgroundSelected();
                    setClickPosition(position);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mRecycleViewType == RECYCLEVIEW_TYPE_EFFECT ?
                EFFECT_ITEM_RES_ARRAY.length : FILTER_ITEM_RES_ARRAY.length;
    }

    /**
     * restore click related state int list
     * @param position
     */
    private void setClickPosition(int position) {
        mItemClickStateList.set(position, true);
        mLastClickPosition = position;
    }

    private void initClickStateList() {
        if (mItemClickStateList == null) {
            return;
        }
        mItemClickStateList.clear();
        if (mRecycleViewType == RECYCLEVIEW_TYPE_EFFECT) {
            mItemClickStateList.addAll(Arrays.asList(
                    new Boolean[EFFECT_ITEM_RES_ARRAY.length]));
            //default effect select item is 1
            setClickPosition(4);
        } else {
            mItemClickStateList.addAll(Arrays.asList(
                    new Boolean[FILTER_ITEM_RES_ARRAY.length]));
            //default filter select item is 0
            setClickPosition(0);
            if (mOnItemSelectedListener != null) {
                mOnItemSelectedListener.onItemSelected(0, mRecycleViewType);
            }
        }

    }

    private OnItemSelectedListener mOnItemSelectedListener;

    void setOnItemSelectedListener(OnItemSelectedListener onItemSelectedListener) {
        mOnItemSelectedListener = onItemSelectedListener;
    }

    interface OnItemSelectedListener {
        void onItemSelected(int itemPosition, int recycleViewType);
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {
        EffectAndFilterItemView mItemView;
        ItemViewHolder(View itemView) {
            super(itemView);
            mItemView = (EffectAndFilterItemView) itemView;
        }
    }
}
