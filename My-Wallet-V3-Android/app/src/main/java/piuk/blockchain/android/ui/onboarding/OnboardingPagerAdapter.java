package piuk.blockchain.android.ui.onboarding;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import piuk.blockchain.android.R;
import piuk.blockchain.android.databinding.ItemOnboardingPagerBinding;

public class OnboardingPagerAdapter extends PagerAdapter {

    private List<OnboardingPagerContent> pages = new ArrayList<>();
    private Context context;

    public OnboardingPagerAdapter(Context context) {
        this.context = context;
    }

    public void notifyPagesChanged(List<OnboardingPagerContent> pages) {
        this.pages = pages;
        // Add empty page
        pages.add(new OnboardingPagerContent(null, null, null, null, null, 0, 0));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        final ItemOnboardingPagerBinding binding =
                DataBindingUtil.inflate(LayoutInflater.from(context),
                        R.layout.item_onboarding_pager,
                        container,
                        false);

        if (position == pages.size() - 1) {
            View view = new View(context);
            container.addView(view);
            return view;
        } else {
            OnboardingPagerContent pagerContent = pages.get(position);

            String heading1 = pagerContent.heading1;
            String heading2 = pagerContent.heading2;
            String content = pagerContent.content;
            String link = pagerContent.link;
            int iconResource = pagerContent.iconResource;
            int colorResource = pagerContent.colorResource;
            String linkAction = pagerContent.linkAction;

            binding.tvHeading1.setText(heading1);
            binding.tvContent.setText(content);
            binding.tvLink.setText(link);

            if (heading2 == null || heading2.isEmpty()) {
                binding.tvHeading2.setVisibility(View.GONE);
            } else {
                binding.tvHeading2.setText(heading2);
            }

            // Set icon
            if (iconResource != 0) {
                binding.ivIcon.setImageResource(iconResource);
            }

            // Set color
            if (colorResource != 0) {
                binding.tvHeading1.setTextColor(ContextCompat.getColor(context, colorResource));
                binding.tvHeading2.setTextColor(ContextCompat.getColor(context, colorResource));
                binding.tvLink.setTextColor(ContextCompat.getColor(context, colorResource));
            }

            binding.getRoot().setOnClickListener(v -> sendBroadcast(linkAction));

            container.addView(binding.getRoot());
            return binding.getRoot();
        }
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }

    @Override
    public int getCount() {
        return pages.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        return super.getItemPosition(object);
    }

    private void sendBroadcast(String linkAction) {
        Intent intent = new Intent(linkAction);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
