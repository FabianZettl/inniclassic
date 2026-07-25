package com.themoon.y1.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.themoon.y1.MainActivity;
import com.themoon.y1.R;
import com.themoon.y1.ThemeManager;
import com.themoon.y1.models.SongItem;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryListAdapter extends BaseAdapter {
    private List<String> items;
    private String type;

    // 🚀 스크롤 할 때 버벅거리지 않도록 이미지를 기억해두는 '메모리 캐시 금고'입니다!
    private static LruCache<String, Drawable> coverCache;

    // 🚀 앨범당 수록곡 개수 (한 번만 계산해서 보관, 매 getView마다 다시 세지 않도록)
    private Map<String, Integer> albumSongCounts;

    public CategoryListAdapter(List<String> items, String type) {
        this.items = items;
        this.type = type;

        if (coverCache == null) {
            coverCache = new LruCache<>(50); // 최대 50개의 앨범 아트를 메모리에 안전하게 기억
        }

        if (type.equals("ALBUM")) {
            albumSongCounts = new HashMap<>();
            for (SongItem song : MainActivity.customLibrary) {
                Integer c = albumSongCounts.get(song.album);
                albumSongCounts.put(song.album, c == null ? 1 : c + 1);
            }
        }
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Object getItem(int position) { return items.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final String name = items.get(position);

        if (type.equals("ALBUM")) {
            return getAlbumRowView(position, convertView, name);
        } else {
            return getSimpleRowView(position, convertView, name);
        }
    }

    // 🚀 [iPod 스타일] 앨범 행: 큼직한 정사각형 커버 + 굵은 앨범명 + 얇은 곡 수 서브타이틀 2줄 구성
    private View getAlbumRowView(final int position, View convertView, final String name) {
        final float d = MainActivity.instance.getResources().getDisplayMetrics().density;
        final int coverSize = (int) (78 * d);

        final LinearLayout row;
        final ImageView ivCover;
        final LinearLayout textStack;
        final TextView tvTitle;
        final TextView tvSubtitle;

        if (convertView instanceof LinearLayout && convertView.getTag() != null && "album_row".equals(convertView.getTag())) {
            row = (LinearLayout) convertView;
            ivCover = (ImageView) row.getChildAt(0);
            textStack = (LinearLayout) row.getChildAt(1);
            tvTitle = (TextView) textStack.getChildAt(0);
            tvSubtitle = (TextView) textStack.getChildAt(1);
        } else {
            row = new LinearLayout(MainActivity.instance);
            row.setTag("album_row");
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setSoundEffectsEnabled(false);
            row.setPadding(0, 0, (int) (10 * d), 0);
            row.setBackground(MainActivity.instance.createButtonBackground(ThemeManager.getListButtonNormalBg()));
            row.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT));

            ivCover = new ImageView(MainActivity.instance);
            ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(coverSize, coverSize);
            ivCover.setLayoutParams(coverLp);
            row.addView(ivCover);

            textStack = new LinearLayout(MainActivity.instance);
            textStack.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textLp.leftMargin = (int) (15 * d);
            textStack.setLayoutParams(textLp);

            tvTitle = new TextView(MainActivity.instance);
            // 🚀 [Main Menu와 폰트 크기 통일] SP 대신 PX 단위로 강제 고정 - 모든 메뉴 화면에서 100% 동일한 렌더링 크기 보장!
            tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 18f * d);
            tvTitle.setTypeface(ThemeManager.getCustomFontBold());
            tvTitle.setSingleLine(true);
            tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            tvTitle.setMarqueeRepeatLimit(-1);
            tvTitle.setHorizontalFadingEdgeEnabled(true);
            textStack.addView(tvTitle);

            tvSubtitle = new TextView(MainActivity.instance);
            tvSubtitle.setTextSize(12f);
            tvSubtitle.setTypeface(ThemeManager.getCustomFont(), Typeface.NORMAL);
            tvSubtitle.setSingleLine(true);
            tvSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = (int) (2 * d);
            tvSubtitle.setLayoutParams(subLp);
            textStack.addView(tvSubtitle);

            row.addView(textStack);
        }

        final boolean isAllSongsRow = MainActivity.ALL_SONGS_SENTINEL.equals(name);

        if (isAllSongsRow) {
            // 🚀 [신규 추가] 아티스트의 앨범 목록 맨 위 "전체 곡" 항목: 검정 배경 + 음표 아이콘, 전체 곡 수 표시
            tvTitle.setText(MainActivity.instance.t("All Songs"));
            int allCount = 0;
            boolean isTrackArtistMode = MainActivity.instance.categoryArtistFilterIsTrackArtist;
            for (SongItem song : MainActivity.customLibrary) {
                String matchField = isTrackArtistMode ? song.artist : song.albumArtist;
                if (MainActivity.instance.categoryArtistFilter.equals(matchField))
                    allCount++;
            }
            tvSubtitle.setText(allCount == 1 ? t1Song() : (allCount + " " + tSongs()));
            // 🚀 [버그 수정] 포커스 리스너가 아직 한 번도 안 불렸을 때도 기본 글자색이 정확히 보이도록 항상 명시적으로 지정!
            boolean focusedNow = row.isFocused();
            tvTitle.setTextColor(focusedNow ? ThemeManager.getListButtonFocusedTextColor() : ThemeManager.getTextColorPrimary());
            tvSubtitle.setTextColor(focusedNow ? ThemeManager.getListButtonFocusedTextColor() : ThemeManager.getTextColorSecondary());
            ivCover.setImageResource(R.drawable.icon_all_songs);

            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        row.setBackground(MainActivity.instance.createFocusedButtonBackground());
                        tvTitle.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                        tvSubtitle.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                        tvTitle.setSelected(true);
                    } else {
                        row.setBackground(MainActivity.instance.createButtonBackground(ThemeManager.getListButtonNormalBg()));
                        tvTitle.setTextColor(ThemeManager.getTextColorPrimary());
                        tvSubtitle.setTextColor(ThemeManager.getTextColorSecondary());
                        tvTitle.setSelected(false);
                    }
                }
            });

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MainActivity.instance.clickFeedback();
                    // 🚀 Artists(트랙 아티스트)에서 들어왔으면 "ARTIST"(song.artist 기준), Album Artists에서
                    // 들어왔으면 기존처럼 "ARTIST_ALL_ALBUMS"(song.albumArtist 기준)로 필터링합니다.
                    MainActivity.instance.virtualQueryType = MainActivity.instance.categoryArtistFilterIsTrackArtist
                            ? "ARTIST" : "ARTIST_ALL_ALBUMS";
                    MainActivity.instance.virtualQueryValue = MainActivity.instance.categoryArtistFilter;
                    MainActivity.instance.currentBrowserMode = MainActivity.BROWSER_VIRTUAL_SONGS;
                    MainActivity.instance.buildVirtualSongs();
                }
            });

            return row;
        }

        tvTitle.setText(name);
        // 🚀 [버그 수정] 포커스 리스너가 아직 한 번도 안 불렸을 때도 기본 글자색이 정확히 보이도록 항상 명시적으로 지정!
        tvTitle.setTextColor(row.isFocused() ? ThemeManager.getListButtonFocusedTextColor() : ThemeManager.getTextColorPrimary());
        int songCount = albumSongCounts != null && albumSongCounts.containsKey(name) ? albumSongCounts.get(name) : 0;
        tvSubtitle.setText(songCount == 1 ? t1Song() : (songCount + " " + tSongs()));
        tvSubtitle.setTextColor(row.isFocused() ? ThemeManager.getListButtonFocusedTextColor() : ThemeManager.getTextColorSecondary());

        // 1. 메모리 금고에 이미 불러온 그림이 있는지 확인!
        Drawable leftDrawable = coverCache.get(name);

        // 2. 금고에 그림이 없다면? 직접 찾아서 그립니다.
        if (leftDrawable == null) {
            String artPath = "";
            byte[] embeddedPic = null;

            // 🚀 [버그 대수술] 이 앨범에 속한 '모든 노래'를 전부 뒤집니다!
            // 특정 곡(예: 3번 트랙)을 재생할 때 다운로드된 이미지가 저장되었더라도,
            // 앨범 카테고리 전체 리스트에서 완벽하게 찾아내도록 조회 범위를 넓힙니다.
            for (SongItem song : MainActivity.customLibrary) {
                if (song.album.equals(name)) {
                    String trackPath = song.file.getAbsolutePath();

                    // ① SharedPreferences 금고에 다운로드 경로가 등록되어 있는지 확인
                    if (MainActivity.instance.prefs != null) {
                        String savedPath = MainActivity.instance.prefs.getString("album_art_" + trackPath, "");
                        if (!savedPath.isEmpty() && new File(savedPath).exists()) {
                            artPath = savedPath;
                            break; // 이미지를 찾았으면 즉시 탈출!
                        }
                    }

                    // ② 금고 등록 정보가 누락되었을 경우를 대비해, 파일 이름 매칭으로 폴더 직접 스캔 더블 체크!
                    String safeFileName = song.file.getName().replace(".mp3", "").replace(".flac", "").replace(".wav", "").replace(".m4a", "").replace(".aac", "").replace(".ogg", "");
                    File manualCoverFile = new File("/storage/sdcard0/Y1_Covers", safeFileName + ".jpg");
                    if (manualCoverFile.exists()) {
                        artPath = manualCoverFile.getAbsolutePath();
                        break; // 실제 파일이 존재하면 즉시 탈출!
                    }

                    // 🚀 [신규 추가] Now Playing 화면과 동일하게, 앨범 폴더 안의 cover.jpg/folder.jpg도 찾아봅니다!
                    File folderCover = MainActivity.instance.findFolderCover(song.file.getParentFile());
                    if (folderCover != null) {
                        artPath = folderCover.getAbsolutePath();
                        break;
                    }

                    // 🚀 ③ 인터넷 이미지가 없다면 파일 내부 내장 아트(Embedded) 후보로 등록 (FLAC 제외, OPUS는 4.0 스캐너 투입!)
                    if (embeddedPic == null && !trackPath.toLowerCase().endsWith(".flac")) {

                        // 🌟 [추가된 4.0 스캐너] Opus 파일일 경우 바주카포 출동!
                        if (trackPath.toLowerCase().endsWith(".opus")) {
                            try {
                                Object[] opusTags = com.themoon.y1.managers.AudioPlayerManager.getInstance().extractOpusMetadata(new File(trackPath));
                                if (opusTags[5] != null) {
                                    embeddedPic = (byte[]) opusTags[5]; // 5번 서랍에 든 앨범 아트 빼오기
                                }
                            } catch (Exception e) {}
                        }
                        // 🌟 기존 파일(MP3 등)은 안드로이드 순정 부품 사용
                        else {
                            android.media.MediaMetadataRetriever mmr = null;
                            java.io.FileInputStream fis = null;
                            try {
                                mmr = new android.media.MediaMetadataRetriever();
                                fis = new java.io.FileInputStream(trackPath);
                                mmr.setDataSource(fis.getFD());
                                byte[] pic = mmr.getEmbeddedPicture();
                                if (pic != null && pic.length > 0) {
                                    embeddedPic = pic;
                                }
                            } catch (Exception e) {
                            } finally {
                                try { if (fis != null) fis.close(); } catch (Exception e) {}
                                try { if (mmr != null) mmr.release(); } catch (Exception e) {}
                            }
                        }
                    }
                }
            }

            Bitmap bmp = null;

            // [선택 1] 인터넷 다운로드 커버가 있으면 최우선 로딩
            if (!artPath.isEmpty()) {
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 4;
                    bmp = BitmapFactory.decodeFile(artPath, opts);
                } catch (Exception e) {}
            }
            // [선택 2] 인터넷 커버가 없으면 파일 내장 아트 로딩
            else if (embeddedPic != null) {
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 4;
                    bmp = BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.length, opts);
                } catch (Exception e) {}
            }

            // [선택 3] 둘 다 없으면 기본 이미지
            if (bmp == null) {
                bmp = BitmapFactory.decodeResource(MainActivity.instance.getResources(), R.drawable.default_album);
            }

            if (bmp != null) {
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, coverSize, coverSize, true);
                leftDrawable = new BitmapDrawable(MainActivity.instance.getResources(), scaled);
                coverCache.put(name, leftDrawable); // 다음번 고속 스크롤을 위해 메모리에 저장
            }
        }

        ivCover.setImageDrawable(leftDrawable);

        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    row.setBackground(MainActivity.instance.createFocusedButtonBackground());
                    tvTitle.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    tvSubtitle.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    MainActivity.instance.showFastScrollLetter(name);
                    tvTitle.setSelected(true);
                } else {
                    row.setBackground(MainActivity.instance.createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    tvTitle.setTextColor(ThemeManager.getTextColorPrimary());
                    tvSubtitle.setTextColor(ThemeManager.getTextColorSecondary());
                    tvTitle.setSelected(false);
                }
            }
        });

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.instance.clickFeedback();
                MainActivity.instance.virtualQueryType = type;
                MainActivity.instance.virtualQueryValue = name;
                MainActivity.instance.currentBrowserMode = MainActivity.BROWSER_VIRTUAL_SONGS;
                MainActivity.instance.buildVirtualSongs();
            }
        });

        return row;
    }

    // 🚀 아티스트 등 그 외 카테고리는 기존처럼 단순 텍스트 한 줄 Button으로 표시
    private View getSimpleRowView(final int position, View convertView, final String name) {
        final Button btn;

        if (convertView instanceof Button) {
            btn = (Button) convertView;
        } else {
            btn = MainActivity.instance.createListButton("");
            btn.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT));
        }

        // 🚀 [iPod 스타일] 아이콘 없이 순수 텍스트만 표시!
        btn.setText(name);
        btn.setCompoundDrawables(null, null, null, null);

        float rowDensity = MainActivity.instance.getResources().getDisplayMetrics().density;
        btn.setPadding((int) (14 * rowDensity), (int) (12 * rowDensity),
                (int) (10 * rowDensity), (int) (12 * rowDensity));

        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    btn.setBackground(MainActivity.instance.createFocusedButtonBackground());
                    btn.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    MainActivity.instance.showFastScrollLetter(name);
                    btn.setSelected(true);
                } else {
                    btn.setBackground(MainActivity.instance.createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    btn.setTextColor(ThemeManager.getTextColorPrimary());
                    btn.setSelected(false);
                }
            }
        });

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.instance.clickFeedback();
                // 🚀 [수정] 아티스트를 클릭하면 바로 곡 목록이 아니라, 그 아티스트의 앨범 목록을 먼저 보여줍니다!
                // (Album Artists든 Artists든 동일한 드릴다운 - 필터링 기준만 다릅니다)
                if (type.equals("ARTIST") || type.equals("TRACK_ARTIST")) {
                    MainActivity.instance.categoryArtistFilter = name;
                    MainActivity.instance.categoryArtistFilterIsTrackArtist = type.equals("TRACK_ARTIST");
                    MainActivity.instance.virtualQueryValue = "";
                    MainActivity.instance.currentBrowserMode = MainActivity.BROWSER_ALBUMS;
                    MainActivity.instance.buildVirtualCategories("ALBUM");
                } else {
                    MainActivity.instance.virtualQueryType = type;
                    MainActivity.instance.virtualQueryValue = name;
                    MainActivity.instance.currentBrowserMode = MainActivity.BROWSER_VIRTUAL_SONGS;
                    MainActivity.instance.buildVirtualSongs();
                }
            }
        });

        return btn;
    }

    private String t1Song() {
        return "1 " + MainActivity.instance.t("Song");
    }

    private String tSongs() {
        return MainActivity.instance.t("Songs");
    }
}
