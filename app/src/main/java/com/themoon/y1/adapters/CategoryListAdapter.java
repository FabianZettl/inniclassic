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
    // 🚀 [ANR 수리 1] 앨범당 파일 목록도 한 번만 모아서 보관 - 예전엔 getView()가 불릴 때마다(=매 스크롤마다)
    // 전체 라이브러리를 처음부터 다시 훑었는데, 곡이 많을수록 매 행 렌더링마다 그 비용이 반복됐습니다.
    private Map<String, List<File>> albumFilesMap;

    public CategoryListAdapter(List<String> items, String type) {
        this.items = items;
        this.type = type;

        if (coverCache == null) {
            coverCache = new LruCache<>(50); // 최대 50개의 앨범 아트를 메모리에 안전하게 기억
        }

        if (type.equals("ALBUM")) {
            albumSongCounts = new HashMap<>();
            albumFilesMap = new HashMap<>();
            for (SongItem song : MainActivity.customLibrary) {
                Integer c = albumSongCounts.get(song.album);
                albumSongCounts.put(song.album, c == null ? 1 : c + 1);

                List<File> files = albumFilesMap.get(song.album);
                if (files == null) {
                    files = new java.util.ArrayList<>();
                    albumFilesMap.put(song.album, files);
                }
                files.add(song.file);
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

    // 🚀 [iPod 스타일] 앨범 행: 작은 정사각형 커버 + 굵은 앨범명 + 얇은 곡 수 서브타이틀 2줄 구성
    // 🚀 [간격 통일] 실제 아이팟처럼 촘촘하게 - 커버를 78dp→40dp로 줄여서 화면에 훨씬 더 많은 앨범이 보이도록!
    private View getAlbumRowView(final int position, View convertView, final String name) {
        final float d = MainActivity.instance.getResources().getDisplayMetrics().density;
        final int coverSize = (int) (52 * d);

        final LinearLayout row;
        final ImageView ivCover;
        final LinearLayout textStack;
        final TextView tvTitle;
        final TextView tvSubtitle;
        final TextView tvArrow;

        if (convertView instanceof LinearLayout && convertView.getTag() != null && "album_row".equals(convertView.getTag())) {
            row = (LinearLayout) convertView;
            ivCover = (ImageView) row.getChildAt(0);
            textStack = (LinearLayout) row.getChildAt(1);
            tvTitle = (TextView) textStack.getChildAt(0);
            tvSubtitle = (TextView) textStack.getChildAt(1);
            tvArrow = (TextView) row.getChildAt(2);
        } else {
            row = new LinearLayout(MainActivity.instance);
            row.setTag("album_row");
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setSoundEffectsEnabled(false);
            row.setPadding((int) (8 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
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
            textLp.leftMargin = (int) (10 * d);
            textStack.setLayoutParams(textLp);

            tvTitle = new TextView(MainActivity.instance);
            // 🚀 [Main Menu와 폰트 크기 통일] SP 대신 PX 단위로 강제 고정 - 모든 메뉴 화면에서 100% 동일한 렌더링 크기 보장!
            tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 21f * d);
            tvTitle.setTypeface(ThemeManager.getCustomFontBold());
            tvTitle.setSingleLine(true);
            tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            tvTitle.setMarqueeRepeatLimit(-1);
            tvTitle.setHorizontalFadingEdgeEnabled(true);
            textStack.addView(tvTitle);

            tvSubtitle = new TextView(MainActivity.instance);
            tvSubtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 14f * d);
            tvSubtitle.setTypeface(ThemeManager.getCustomFont(), Typeface.NORMAL);
            tvSubtitle.setSingleLine(true);
            tvSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = (int) (1 * d);
            tvSubtitle.setLayoutParams(subLp);
            textStack.addView(tvSubtitle);

            row.addView(textStack);

            // 🚀 포커스된 행에만 나타나는 실제 아이팟 스타일 우측 화살표
            tvArrow = new TextView(MainActivity.instance);
            tvArrow.setText("〉");
            tvArrow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 18f * d);
            tvArrow.setVisibility(View.GONE);
            LinearLayout.LayoutParams lpArrow = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpArrow.leftMargin = (int) (6 * d);
            tvArrow.setLayoutParams(lpArrow);
            row.addView(tvArrow);
        }

        // 🚀 [흰색 화살표 고정] Songs 목록(createListButtonWithIcon)과 동일하게 테마색이 아닌 순백색으로 고정합니다.
        tvArrow.setTextColor(0xFFFFFFFF);
        tvArrow.setVisibility(row.isFocused() ? View.VISIBLE : View.GONE);

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
                        tvArrow.setVisibility(View.VISIBLE);
                        tvTitle.setSelected(true);
                    } else {
                        row.setBackground(MainActivity.instance.createButtonBackground(ThemeManager.getListButtonNormalBg()));
                        tvTitle.setTextColor(ThemeManager.getTextColorPrimary());
                        tvSubtitle.setTextColor(ThemeManager.getTextColorSecondary());
                        tvArrow.setVisibility(View.GONE);
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

        // 🚀 [ANR 수리 2] 예전에는 이 자리에서 SharedPreferences/파일 존재 확인/MediaMetadataRetriever까지
        // 전부 동기적으로(=메인/UI 스레드에서) 수행했습니다 - 특히 MediaMetadataRetriever는 느리고, 일부
        // 파일에서는 오래 걸리거나 멈춰서, 앨범이 많은 라이브러리에서는 스크롤할 때마다 매 행이 이 전체
        // 과정을 반복해 화면이 멈추거나(ANR) 완전히 까맣게 나오는 원인이었습니다("Albums" 진입 시 먹통).
        // 1. 메모리 금고에 이미 불러온 그림이 있으면 즉시 표시(공짜)!
        Drawable cached = coverCache.get(name);
        if (cached != null) {
            ivCover.setImageDrawable(cached);
        } else {
            // 2. 없다면 일단 기본 이미지를 보여주고, 실제 검색/디코딩은 백그라운드 스레드로 넘깁니다.
            ivCover.setImageResource(R.drawable.default_album);
            ivCover.setTag(name);
            loadAlbumArtAsync(name, coverSize, ivCover);
        }

        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    row.setBackground(MainActivity.instance.createFocusedButtonBackground());
                    tvTitle.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    tvSubtitle.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    tvArrow.setVisibility(View.VISIBLE);
                    MainActivity.instance.showFastScrollLetter(name);
                    tvTitle.setSelected(true);
                } else {
                    row.setBackground(MainActivity.instance.createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    tvTitle.setTextColor(ThemeManager.getTextColorPrimary());
                    tvSubtitle.setTextColor(ThemeManager.getTextColorSecondary());
                    tvArrow.setVisibility(View.GONE);
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

    // 🚀 [ANR 수리 3] 실제 검색+디코딩을 백그라운드 스레드에서 수행하고, 끝나면 메인 스레드에서 그 행이
    // (재활용되어 다른 앨범으로 바뀌지 않고) 여전히 같은 앨범을 보여주고 있을 때만 이미지를 반영합니다.
    private void loadAlbumArtAsync(final String name, final int coverSize, final ImageView targetView) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Drawable result = fetchAlbumArtDrawable(name, coverSize);
                if (result != null) {
                    coverCache.put(name, result);
                }
                MainActivity.instance.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (name.equals(targetView.getTag())) {
                            targetView.setImageDrawable(result);
                        }
                    }
                });
            }
        }).start();
    }

    // 🚀 기존 동기 로직을 그대로 옮긴 것 - 순서(내려받은 커버 -> 수동 커버 폴더 -> 폴더 커버 -> 내장 아트 ->
    // 기본 이미지)는 100% 동일하게 유지, 다만 이제 UI 스레드가 아닌 곳에서 호출됩니다.
    private Drawable fetchAlbumArtDrawable(String name, int coverSize) {
        String artPath = "";
        byte[] embeddedPic = null;

        List<File> albumFiles = albumFilesMap != null ? albumFilesMap.get(name) : null;
        if (albumFiles != null) {
            for (File file : albumFiles) {
                String trackPath = file.getAbsolutePath();

                // ① SharedPreferences 금고에 다운로드 경로가 등록되어 있는지 확인
                if (MainActivity.instance.prefs != null) {
                    String savedPath = MainActivity.instance.prefs.getString("album_art_" + trackPath, "");
                    if (!savedPath.isEmpty() && new File(savedPath).exists()) {
                        artPath = savedPath;
                        break; // 이미지를 찾았으면 즉시 탈출!
                    }
                }

                // ② 금고 등록 정보가 누락되었을 경우를 대비해, 파일 이름 매칭으로 폴더 직접 스캔 더블 체크!
                String safeFileName = file.getName().replace(".mp3", "").replace(".flac", "").replace(".wav", "").replace(".m4a", "").replace(".aac", "").replace(".ogg", "");
                File manualCoverFile = new File("/storage/sdcard0/Y1_Covers", safeFileName + ".jpg");
                if (manualCoverFile.exists()) {
                    artPath = manualCoverFile.getAbsolutePath();
                    break; // 실제 파일이 존재하면 즉시 탈출!
                }

                // 🚀 Now Playing 화면과 동일하게, 앨범 폴더 안의 cover.jpg/folder.jpg도 찾아봅니다!
                File folderCover = MainActivity.instance.findFolderCover(file.getParentFile());
                if (folderCover != null) {
                    artPath = folderCover.getAbsolutePath();
                    break;
                }

                // 🚀 ③ 인터넷 이미지가 없다면 파일 내부 내장 아트(Embedded) 후보로 등록
                // (FLAC/OPUS는 전용 파서 투입 - 예전엔 FLAC이 아예 통째로 제외되어 있었는데, 그러면 폴더
                // 커버/수동 커버도 없는 FLAC 앨범은 표지가 절대 안 뜨는 원인이었습니다. Now Playing/Cover
                // Flow는 이미 이 전용 FLAC 파서로 아트를 뽑고 있었으니 여기도 똑같이 맞춥니다.)
                if (embeddedPic == null) {

                    // 🌟 Opus 파일일 경우 바주카포 출동!
                    if (trackPath.toLowerCase().endsWith(".opus")) {
                        try {
                            Object[] opusTags = com.themoon.y1.managers.AudioPlayerManager.getInstance().extractOpusMetadata(file);
                            if (opusTags[5] != null) {
                                embeddedPic = (byte[]) opusTags[5]; // 5번 서랍에 든 앨범 아트 빼오기
                            }
                        } catch (Exception e) {}
                    }
                    // 🌟 FLAC은 자체 METADATA_BLOCK_PICTURE 파서로 (MediaMetadataRetriever는 FLAC에서 느리거나
                    // 멈출 수 있어 원래부터 제외되어 있었고, 그건 그대로 유지합니다 - 대신 전용 파서를 씁니다)
                    else if (trackPath.toLowerCase().endsWith(".flac")) {
                        try {
                            Object[] flacTags = com.themoon.y1.managers.AudioPlayerManager.getInstance().extractFlacMetadata(file);
                            if (flacTags[5] != null) {
                                embeddedPic = (byte[]) flacTags[5];
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

        if (bmp == null) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(bmp, coverSize, coverSize, true);
        return new BitmapDrawable(MainActivity.instance.getResources(), scaled);
    }

    // 🚀 아티스트 등 그 외 카테고리는 실제 아이팟처럼 촘촘한 한 줄 + 포커스일 때만 뜨는 우측 화살표로 표시
    private View getSimpleRowView(final int position, View convertView, final String name) {
        final LinearLayout row;
        final TextView tvMain;
        final TextView tvArrow;

        if (convertView instanceof LinearLayout && "simple_row".equals(convertView.getTag())) {
            row = (LinearLayout) convertView;
            tvMain = (TextView) row.getChildAt(0);
            tvArrow = (TextView) row.getChildAt(1);
        } else {
            final float d = MainActivity.instance.getResources().getDisplayMetrics().density;

            row = new LinearLayout(MainActivity.instance);
            row.setTag("simple_row");
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setSoundEffectsEnabled(false);
            // 🚀 [간격 통일] 실제 아이팟처럼 촘촘하게 - 위아래 여백을 대폭 줄여서 화면에 훨씬 더 많은 항목이 보이도록!
            // 🚀 [좌측 정렬] 상태바 타이틀과 동일한 8dp에서 시작하도록 왼쪽 여백을 맞춥니다.
            row.setPadding((int) (8 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
            row.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT));

            tvMain = new TextView(MainActivity.instance);
            tvMain.setSingleLine(true);
            tvMain.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            tvMain.setMarqueeRepeatLimit(-1);
            tvMain.setHorizontalFadingEdgeEnabled(true);
            tvMain.setTypeface(ThemeManager.getCustomFontBold());
            tvMain.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 21f * d);
            LinearLayout.LayoutParams lpMain = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvMain.setLayoutParams(lpMain);
            row.addView(tvMain);

            // 🚀 포커스된 행에만 나타나는 실제 아이팟 스타일 우측 화살표
            tvArrow = new TextView(MainActivity.instance);
            tvArrow.setText("〉");
            tvArrow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 18f * d);
            tvArrow.setVisibility(View.GONE);
            LinearLayout.LayoutParams lpArrow = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpArrow.leftMargin = (int) (6 * d);
            tvArrow.setLayoutParams(lpArrow);
            row.addView(tvArrow);
        }

        tvMain.setText(name);

        // 🚀 [버그 수정] 재활용된 행이 실제로는 포커스 상태인데도 무조건 평상시 배경으로 덮어써버리던
        // 문제를 수정 - 포커스 상태를 그대로 반영합니다.
        boolean isFocusedNow = row.isFocused();
        row.setBackground(isFocusedNow ? MainActivity.instance.createFocusedButtonBackground()
                : MainActivity.instance.createButtonBackground(ThemeManager.getListButtonNormalBg()));
        tvMain.setTextColor(isFocusedNow ? ThemeManager.getListButtonFocusedTextColor() : ThemeManager.getTextColorPrimary());
        // 🚀 [흰색 화살표 고정] Songs 목록(createListButtonWithIcon)과 동일하게 테마색이 아닌 순백색으로 고정합니다.
        tvArrow.setTextColor(0xFFFFFFFF);
        tvArrow.setVisibility(isFocusedNow ? View.VISIBLE : View.GONE);

        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    row.setBackground(MainActivity.instance.createFocusedButtonBackground());
                    tvMain.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    tvArrow.setVisibility(View.VISIBLE);
                    MainActivity.instance.showFastScrollLetter(name);
                    tvMain.setSelected(true);
                } else {
                    row.setBackground(MainActivity.instance.createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    tvMain.setTextColor(ThemeManager.getTextColorPrimary());
                    tvArrow.setVisibility(View.GONE);
                    tvMain.setSelected(false);
                }
            }
        });

        row.setOnClickListener(new View.OnClickListener() {
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

        return row;
    }

    private String t1Song() {
        return "1 " + MainActivity.instance.t("Song");
    }

    private String tSongs() {
        return MainActivity.instance.t("Songs");
    }
}
