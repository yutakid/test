package rejasupotaro.rebuild.fragments;

import com.squareup.otto.Subscribe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

import javax.inject.Inject;

import rejasupotaro.rebuild.R;
import rejasupotaro.rebuild.dialogs.ChooseEpisodePlayFormatDialog;
import rejasupotaro.rebuild.events.BusProvider;
import rejasupotaro.rebuild.events.EpisodePlayStartEvent;
import rejasupotaro.rebuild.events.LoadEpisodeListCompleteEvent;
import rejasupotaro.rebuild.events.ReceivePauseActionEvent;
import rejasupotaro.rebuild.events.ReceiveResumeActionEvent;
import rejasupotaro.rebuild.listener.LoadListener;
import rejasupotaro.rebuild.listener.OnPlayerSeekListener;
import rejasupotaro.rebuild.media.PodcastPlayer;
import rejasupotaro.rebuild.models.Episode;
import rejasupotaro.rebuild.notifications.PodcastPlayerNotification;
import rejasupotaro.rebuild.tools.OnContextExecutor;
import rejasupotaro.rebuild.utils.DateUtils;
import rejasupotaro.rebuild.utils.UiAnimations;
import rejasupotaro.rebuild.views.StateFrameLayout;
import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;

public class EpisodeMediaFragment extends RoboFragment {

    private Episode episode;

    private LoadListener loadListener;

    @Inject
    private OnContextExecutor onContextExecutor;

    @Inject
    private EpisodePlayDialogHelper episodePlayDialogHelper;

    @InjectView(R.id.episode_title)
    private TextView episodeTitleTextView;

    @InjectView(R.id.episode_detail_header_cover)
    private View mediaStartButtonOnImageCover;

    @InjectView(R.id.media_current_time)
    private TextView mediaCurrentTimeTextView;

    @InjectView(R.id.media_duration)
    private TextView mediaDurationTextView;

    @InjectView(R.id.media_play_and_pause_button)
    private CheckBox mediaPlayAndPauseButton;

    @InjectView(R.id.media_seekbar)
    private SeekBar seekBar;

    @InjectView(R.id.state_frame_layout)
    private StateFrameLayout stateFrameLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        BusProvider.getInstance().register(this);
        return inflater.inflate(R.layout.fragment_episode_media, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        BusProvider.getInstance().unregister(this);
        super.onDestroyView();
    }

    public void setup(final Episode episode) {
        if (episode == null) {
            getActivity().finish();
        }
        this.episode = episode;

        loadListener = new LoadListener() {
            @Override
            public void showProgress() {
                stateFrameLayout.showProgress();
            }

            @Override
            public void showError() {
            }

            @Override
            public void showContent() {
                stateFrameLayout.showContent();
            }
        };

        String originalTitle = episode.getTitle();
        int startIndex = originalTitle.indexOf(':');
        episodeTitleTextView.setText(originalTitle.substring(startIndex + 2));

        setupMediaPlayAndPauseButton(episode);
        setupSeekBar(episode);
    }

    private void setupMediaPlayAndPauseButton(final Episode episode) {
        if (episode.getEnclosure() == null) {
            return;
        }

        PodcastPlayer podcastPlayer = PodcastPlayer.getInstance();
        if (podcastPlayer.isPlayingEpisode(episode)
                && (podcastPlayer.isPlaying() || podcastPlayer.isPaused())) {
            mediaStartButtonOnImageCover.setVisibility(View.GONE);

            if (podcastPlayer.isPlaying()) {
                mediaPlayAndPauseButton.setChecked(true);
            } else {
                mediaPlayAndPauseButton.setChecked(false);
            }
        } else {
            if (podcastPlayer.isPlaying()) {
                mediaStartButtonOnImageCover.setVisibility(View.VISIBLE);
                mediaStartButtonOnImageCover.setAlpha(1);
            }
        }

        mediaPlayAndPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayAndPauseButton.setChecked(!mediaPlayAndPauseButton.isChecked());

                ChooseEpisodePlayFormatDialog dialog
                        = ChooseEpisodePlayFormatDialog.newInstance(episode);
                dialog.show(getActivity().getSupportFragmentManager(), "");

                /* TODO: impl me
                if (mediaPlayAndPauseButton.isChecked()) {
                    start(episode);
                } else {
                    pause(episode);
                }
                */
            }
        });
    }

    private void start(final Episode episode) {
        final PodcastPlayer podcastPlayer = PodcastPlayer.getInstance();
        if (shouldRestart(episode)) {
            podcastPlayer.restart();
            seekBar.setEnabled(true);
            PodcastPlayerNotification.notify(getActivity(), episode);
        } else {
            loadListener.showProgress();
            mediaPlayAndPauseButton.setEnabled(false);
            podcastPlayer.start(getActivity(), episode, new PodcastPlayer.StateChangedListener() {
                @Override
                public void onStart() {
                    if (getActivity() == null) {
                        pause(episode);
                    } else {
                        loadListener.showContent();
                        UiAnimations.fadeOut(mediaStartButtonOnImageCover, 300, 1000);

                        seekBar.setEnabled(true);
                        mediaPlayAndPauseButton.setEnabled(true);
                        PodcastPlayerNotification.notify(getActivity(), episode);
                        BusProvider.getInstance().post(new EpisodePlayStartEvent(episode.getEpisodeId()));
                    }
                }
            });
        }
    }

    private boolean shouldRestart(Episode episode) {
        PodcastPlayer podcastPlayer = PodcastPlayer.getInstance();
        return (podcastPlayer.isPlayingEpisode(episode)
                && (podcastPlayer.isPlaying() || podcastPlayer.isPaused()));
    }

    private void pause(final Episode episode) {
        final PodcastPlayer podcastPlayer = PodcastPlayer.getInstance();
        podcastPlayer.pause();
        seekBar.setEnabled(false);
        PodcastPlayerNotification
                .notify(getActivity(), episode, PodcastPlayer.getInstance().getCurrentPosition());
    }

    private void setupSeekBar(final Episode episode) {
        mediaDurationTextView.setText(episode.getDuration());

        if (PodcastPlayer.getInstance().isPlaying()) {
            updateCurrentTime(PodcastPlayer.getInstance().getCurrentPosition());
        } else {
            updateCurrentTime(0);
        }

        seekBar.setOnSeekBarChangeListener(new OnPlayerSeekListener());
        seekBar.setMax(DateUtils.durationToInt(episode.getDuration()));
        if (PodcastPlayer.getInstance().isPlayingEpisode(episode)) {
            seekBar.setEnabled(true);
        } else {
            seekBar.setEnabled(false);
        }

        PodcastPlayer.getInstance().setCurrentTimeListener(
                new PodcastPlayer.CurrentTimeListener() {
                    @Override
                    public void onTick(int currentPosition) {
                        if (PodcastPlayer.getInstance().isPlayingEpisode(episode)) {
                            updateCurrentTime(currentPosition);
                            PodcastPlayerNotification
                                    .notify(getActivity(), episode, currentPosition);
                        } else {
                            updateCurrentTime(0);
                        }
                    }
                });
    }

    private void updateCurrentTime(int currentPosition) {
        mediaCurrentTimeTextView.setText(DateUtils.formatCurrentTime(currentPosition));
        seekBar.setProgress(currentPosition);
    }

    @Subscribe
    public void onReceivePauseAction(ReceivePauseActionEvent event) {
        onContextExecutor.execute(getActivity(), new Runnable() {
            @Override
            public void run() {
                mediaPlayAndPauseButton.setChecked(false);
            }
        });
    }

    @Subscribe
    public void onReceivePauseAction(ReceiveResumeActionEvent event) {
        onContextExecutor.execute(getActivity(), new Runnable() {
            @Override
            public void run() {
                mediaPlayAndPauseButton.setChecked(true);
            }
        });
    }

    @Subscribe
    public void onLoadEpisodeListComplete(final LoadEpisodeListCompleteEvent event) {
        if (episode != null) {
            return;
        }

        onContextExecutor.execute(getActivity(), new Runnable() {
            @Override
            public void run() {
                List<Episode> episodeList = event.getEpisodeList();

                if (episodeList == null || episodeList.size() == 0) {
                    return;
                }
                Episode episode = episodeList.get(0);

                if (PodcastPlayer.getInstance().isPlaying()) {
                    return;
                }

                setup(episode);
            }
        });
    }
}
