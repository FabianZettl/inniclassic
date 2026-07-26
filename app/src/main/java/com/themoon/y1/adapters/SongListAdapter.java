package com.themoon.y1.adapters;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;

import com.themoon.y1.MainActivity;
import com.themoon.y1.ThemeManager;
import com.themoon.y1.models.SongItem;

import java.util.List;

public class SongListAdapter extends BaseAdapter {
    private List<SongItem> items;

    public SongListAdapter(List<SongItem> items) {
        this.items = items;
    }

    // 🚀 [iPod 스타일] "All Songs" 목록 맨 위에 순정 기기처럼 "Shuffle" 항목을 하나 더 보여줍니다.
    private boolean hasShuffleRow() {
        return "ALL".equals(MainActivity.instance.virtualQueryType) && !items.isEmpty();
    }

    @Override
    public int getCount() { return items.size() + (hasShuffleRow() ? 1 : 0); }

    @Override
    public Object getItem(int position) {
        if (hasShuffleRow()) {
            if (position == 0) return null;
            return items.get(position - 1);
        }
        return items.get(position);
    }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (hasShuffleRow() && position == 0) {
            return getShuffleRowView(convertView);
        }
        final int songPosition = hasShuffleRow() ? position - 1 : position;

        final android.view.View btn;
        final SongItem song = items.get(songPosition);

        String iconCode = MainActivity.instance.isAudiobookLibraryMode ? "\uE310" : "\uE405";
        String displayTitle = song.title;
        int customColor = 0;

        if (MainActivity.instance != null) {


            // ==========================================
            // 🚀 1. 팟캐스트 에피소드 모드 (시간 표시 및 로직 완벽 통합!)
            // ==========================================
            if (MainActivity.instance.currentBrowserMode == 14) {
                String audioUrl = song.genre;
                String channelName = song.artist;
                String pubDate = song.year;
                String datePrefix = (pubDate != null && !pubDate.isEmpty()) ? "[" + pubDate.trim() + "] " : "";

                String safeChannel = channelName.replaceAll("[\\\\/:*?\"<>|]", "_");
                String safeTitle = song.title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".mp3";
                java.io.File localFile = new java.io.File("/storage/sdcard0/Podcasts/" + safeChannel, safeTitle);

                // ⏱ 저장된 시간 가져오기
                int savedPos = MainActivity.instance.prefs.getInt("book_pos_" + localFile.getAbsolutePath(), 0);
                if (savedPos == 0) {
                    String streamKey = "/PODCAST_STREAM/" + safeChannel + "/" + safeTitle;
                    savedPos = MainActivity.instance.prefs.getInt("book_pos_" + streamKey, 0);
                }

                String progressText = "";
                if (savedPos > 0) {
                    long min = (savedPos / 1000) / 60;
                    long sec = (savedPos / 1000) % 60;
                    progressText = String.format(" [⏱ %02d:%02d]", min, sec);
                }

                // 타이틀 및 색상 결정
                if (MainActivity.instance.activePodcastDownloads.containsKey(audioUrl)) {
                    int prog = 0;
                    if (MainActivity.instance.podcastDownloadProgress.containsKey(audioUrl)) {
                        prog = MainActivity.instance.podcastDownloadProgress.get(audioUrl);
                    }
                    displayTitle = "⏳ [" + prog + "%] " +datePrefix +  song.title;
                    customColor = 0xFFFF8800; // 오렌지색
                } else if (localFile.exists() && localFile.length() > 0) {
                    displayTitle = "✔ " +datePrefix +  song.title + progressText;
                    customColor = 0xFF00FF00; // 초록색
                } else {
                    displayTitle = datePrefix + song.title + progressText;
                }

            }
            // ==========================================
            // 📅 2. '최근 추가된 곡' 모드일 때
            // ==========================================
            else if ("RECENT".equals(MainActivity.instance.virtualQueryType)) {
                long lastMod = song.file.lastModified();
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0);
                long todayStart = cal.getTimeInMillis();
                long yesterdayStart = todayStart - (24 * 60 * 60 * 1000);
                String datePrefix = "";
                if (lastMod >= todayStart) datePrefix = "[" + MainActivity.instance.t("Today") + "] ";
                else if (lastMod >= yesterdayStart) datePrefix = "[" + MainActivity.instance.t("Yesterday") + "] ";
                else {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yy.MM.dd");
                    datePrefix = "[" + sdf.format(new java.util.Date(lastMod)) + "] ";
                }
                displayTitle = datePrefix + song.title;
            }
            // 🔍 3. [iPod 스타일] 검색 결과 모드일 때 - 무엇 때문에 매칭되었는지 작은 힌트를 붙여줍니다
            else if ("SEARCH".equals(MainActivity.instance.virtualQueryType)) {
                String q = MainActivity.instance.virtualQueryValue == null ? ""
                        : MainActivity.instance.virtualQueryValue.toLowerCase();
                boolean titleMatch = song.title != null && song.title.toLowerCase().contains(q);
                if (!titleMatch && song.artist != null && song.artist.toLowerCase().contains(q)) {
                    displayTitle = song.title + "  —  " + song.artist;
                } else if (!titleMatch && song.album != null && song.album.toLowerCase().contains(q)) {
                    displayTitle = song.title + "  —  " + song.album;
                }
            }
        }

        // 🚀 UI 그리기 (계산된 텍스트와 색상을 여기서 한 번에 입힙니다)
        if (convertView == null) {
            btn = MainActivity.instance.createListButtonWithIcon(iconCode, displayTitle, customColor);
            btn.setLayoutParams(new android.widget.AbsListView.LayoutParams(android.widget.AbsListView.LayoutParams.MATCH_PARENT, android.widget.AbsListView.LayoutParams.WRAP_CONTENT));
        } else {
            btn = convertView;
            if (btn instanceof android.widget.LinearLayout) {
                android.widget.LinearLayout layout = (android.widget.LinearLayout) btn;
                if (layout.getChildCount() > 1) {
                    android.widget.TextView tvIcon = (android.widget.TextView) layout.getChildAt(0);
                    android.widget.TextView tvText = (android.widget.TextView) layout.getChildAt(1);
                    tvIcon.setText(iconCode);
                    tvText.setText(displayTitle);
                    int applyColor = (customColor != 0) ? customColor : com.themoon.y1.ThemeManager.getTextColorPrimary();
                    if (!btn.hasFocus()) { tvIcon.setTextColor(applyColor); tvText.setTextColor(applyColor); }
                }
            }
        }

        boolean hasProgress = false;
        if (MainActivity.instance.isAudiobookLibraryMode) {
            int pos = MainActivity.instance.prefs.getInt("book_pos_" + song.file.getAbsolutePath(), 0);
            int dur = MainActivity.instance.prefs.getInt("book_dur_" + song.file.getAbsolutePath(), 0);
            if (pos > 0 && dur > 0) {
                MainActivity.instance.setupAudiobookProgress(btn, pos, dur);
                hasProgress = true;
            }
        }
// =======================================================
        // 🚀 2. [추가] 팟캐스트 에피소드 배경 프로그레스 바 연동!
        // =======================================================
        if (MainActivity.instance.currentBrowserMode == 14) {
            String safeChannel = song.artist.replaceAll("[\\\\/:*?\"<>|]", "_");
            String safeTitle = song.title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".mp3";
            java.io.File localFile = new java.io.File("/storage/sdcard0/Podcasts/" + safeChannel, safeTitle);

            String streamKey = "/PODCAST_STREAM/" + safeChannel + "/" + safeTitle;

            // 💡 로컬(다운로드) 파일 재생 기록부터 찾기
            int pos = MainActivity.instance.prefs.getInt("book_pos_" + localFile.getAbsolutePath(), 0);
            int dur = MainActivity.instance.prefs.getInt("book_dur_" + localFile.getAbsolutePath(), 0);

            // 💡 다운로드 기록이 없으면 스트리밍 재생 기록 찾기
            if (pos == 0 || dur == 0) {
                pos = MainActivity.instance.prefs.getInt("book_pos_" + streamKey, 0);
                dur = MainActivity.instance.prefs.getInt("book_dur_" + streamKey, 0);
            }

            // 🎯 기억된 시간과 곡의 전체 길이가 존재하면 오디오북과 동일한 배경 진행률 효과 발사!
            if (pos > 0 && dur > 0) {
                MainActivity.instance.setupAudiobookProgress(btn, pos, dur);
                hasProgress = true;
            }
        }
        if (!hasProgress) {
            applyDefaultFocusListener(btn, song.title, customColor);
        }

        // 🚀 [클릭 이벤트 처리]
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.instance.clickFeedback();

                // 팟캐스트 인터셉터
                if (MainActivity.instance.currentBrowserMode == 14) {
                    String audioUrl = song.genre;
                    String imageUrl = song.album;
                    String channelName = song.artist;

                    String safeChannel = channelName.replaceAll("[\\\\/:*?\"<>|]", "_");
                    String safeTitle = song.title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".mp3";
                    java.io.File localFile = new java.io.File("/storage/sdcard0/Podcasts/" + safeChannel, safeTitle);

                    if (localFile.exists() && localFile.length() > 0) {
                        int savedPos = MainActivity.instance.prefs.getInt("book_pos_" + localFile.getAbsolutePath(), 0);
                        if (savedPos == 0) {
                            String streamKey = "/PODCAST_STREAM/" + safeChannel + "/" + safeTitle;
                            savedPos = MainActivity.instance.prefs.getInt("book_pos_" + streamKey, 0);
                        }

                        // =======================================================
                        // 🚀 [연속 재생 엔진 탑재!]
                        // 현재 채널 리스트(items)를 훑어서 '다운로드된' 파일들만 싹 다 바구니에 담습니다.
                        // =======================================================
                        java.util.List<java.io.File> playList = new java.util.ArrayList<>();
                        int targetIdx = 0;

                        for (SongItem ep : items) {
                            String epTitle = ep.title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".mp3";
                            java.io.File epFile = new java.io.File("/storage/sdcard0/Podcasts/" + safeChannel, epTitle);

                            // 파일이 기기에 실제로 존재할 때만 바구니에 합류!
                            if (epFile.exists() && epFile.length() > 0) {
                                playList.add(epFile);
                                // 지금 내가 누른 이 파일이 바구니의 몇 번째(인덱스)에 담겼는지 추적합니다.
                                if (epFile.getAbsolutePath().equals(localFile.getAbsolutePath())) {
                                    targetIdx = playList.size() - 1;
                                }
                            }
                        }

                        // 바구니 통째로(playList)와 내가 누른 곡 번호(targetIdx)를 엔진에 장전!
                        if (savedPos > 0) {
                            com.themoon.y1.managers.AudioPlayerManager.getInstance().playTrackListWithOffset(playList, targetIdx, savedPos);
                        } else {
                            com.themoon.y1.managers.AudioPlayerManager.getInstance().playTrackList(playList, targetIdx);
                        }
                        MainActivity.instance.changeScreen(3);
                    } else {
                        MainActivity.instance.showPodcastActionDialog(song.title, audioUrl, imageUrl, channelName);
                    }

                    // 🚨 [핵심] 여기서 반드시 리턴(탈출)해야 에러가 발생하지 않습니다!
                    return;
                }

                // 🎵 일반 음악 재생 (팟캐스트가 아닐 때만 여기로 내려옵니다)
                com.themoon.y1.managers.AudioPlayerManager.getInstance().playTrackList(MainActivity.instance.virtualSongList, songPosition);
                MainActivity.instance.changeScreen(3);
            }
        });

        // 롱클릭 이벤트
        btn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                MainActivity.instance.clickFeedback();
                MainActivity.instance.isLongPressConsumed = true;

                if (MainActivity.instance.currentBrowserMode == 5) {
                    MainActivity.instance.showRemoveFromFavoritesDialog(song.file);
                }else if (MainActivity.instance.currentBrowserMode == 7) {
                    MainActivity.instance.showRemoveFromPlaylistDialog(song.file);
                }
                 else {
                    MainActivity.instance.showAddToPlaylistDialog(song.file);
                }
                return true;
            }
        });

        return btn;
    }

    // 🚀 [iPod 스타일] "All Songs" 맨 위 고정 "Shuffle" 항목 - 라이브러리 전체를 섞어서 바로 재생 시작!
    private View getShuffleRowView(View convertView) {
        View row;
        if (convertView != null && "shuffle_row".equals(convertView.getTag())) {
            row = convertView;
        } else {
            row = MainActivity.instance.createListButtonWithIcon("", MainActivity.instance.t("Shuffle"));
            row.setTag("shuffle_row");
            row.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.instance.clickFeedback();
                java.util.List<java.io.File> shuffled = new java.util.ArrayList<>(MainActivity.instance.virtualSongList);
                java.util.Collections.shuffle(shuffled);
                com.themoon.y1.managers.AudioPlayerManager.getInstance().playTrackList(shuffled, 0);
                MainActivity.instance.changeScreen(3);
            }
        });
        row.setOnLongClickListener(null);

        return row;
    }

    // 🚀 [신규] 유니코드 아이콘 뷰(LinearLayout)와 순정 버튼(Button)을 모두 지원하는 하이브리드 포커스 리스너!
    private void applyDefaultFocusListener(final android.view.View btn, final String title, final int customColor) {
        final int normalColor = (customColor != 0) ? customColor : com.themoon.y1.ThemeManager.getTextColorPrimary();

        // 1. 리스너를 미리 변수(listener)에 담아둡니다.
        final android.view.View.OnFocusChangeListener listener = new android.view.View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(android.view.View v, boolean hasFocus) {
                if (hasFocus) {
                    btn.setBackground(MainActivity.instance.createButtonBackground(com.themoon.y1.ThemeManager.getListButtonFocusedBg()));

                    if (v instanceof android.widget.LinearLayout) {
                        android.widget.LinearLayout row = (android.widget.LinearLayout) v;
                        if (row.getChildCount() > 1) {
                            ((android.widget.TextView) row.getChildAt(0)).setTextColor(com.themoon.y1.ThemeManager.getListButtonFocusedTextColor());
                            android.widget.TextView tvText = (android.widget.TextView) row.getChildAt(1);
                            tvText.setTextColor(com.themoon.y1.ThemeManager.getListButtonFocusedTextColor());
                            tvText.setSelected(true); // 🚀 텍스트 흐르기 가동!
                        }
                    } else if (v instanceof android.widget.Button) {
                        ((android.widget.Button) v).setTextColor(com.themoon.y1.ThemeManager.getListButtonFocusedTextColor());
                        v.setSelected(true); // 🚀 텍스트 흐르기 가동!
                    }

                    MainActivity.instance.showFastScrollLetter(title);
                } else {
                    btn.setBackground(MainActivity.instance.createButtonBackground(com.themoon.y1.ThemeManager.getListButtonNormalBg()));

                    if (v instanceof android.widget.LinearLayout) {
                        android.widget.LinearLayout row = (android.widget.LinearLayout) v;
                        if (row.getChildCount() > 1) {
                            ((android.widget.TextView) row.getChildAt(0)).setTextColor(normalColor);
                            android.widget.TextView tvText = (android.widget.TextView) row.getChildAt(1);
                            tvText.setTextColor(normalColor);
                            tvText.setSelected(false); // 🚀 텍스트 흐르기 정지!
                        }
                    } else if (v instanceof android.widget.Button) {
                        ((android.widget.Button) v).setTextColor(normalColor);
                        v.setSelected(false); // 🚀 텍스트 흐르기 정지!
                    }
                }
            }
        };

        // 2. 버튼에 리스너를 장착!
        btn.setOnFocusChangeListener(listener);

        // =======================================================
        // 🚀 [마키 버그 완벽 수리] 리스트 뷰 재활용 시 포커스 증발 방지 엔진!
        // =======================================================
        btn.post(new Runnable() {
            @Override
            public void run() {
                // UI가 화면에 완전히 그려진 직후, 포커스를 쥐고 있다면 억지로 한 번 이벤트를 쏴서 깨워줍니다!
                if (btn.isFocused()) {
                    listener.onFocusChange(btn, true);
                }
            }
        });
    }
}