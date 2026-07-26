package com.themoon.y1.models; // 본인의 패키지 경로에 맞게 유지해주세요!

import java.io.File;

public class SongItem {
    public File file;
    public String title;
    public String artist;
    public String album;

    // 🚀 [신규 추가] 연도와 장르를 기억할 공간 선언!
    public String year;
    public String genre;

    // 🚀 [신규 추가] "Artists" 탭 그룹핑용 앨범 아티스트 (ALBUMARTIST 태그, 없으면 artist로 대체)
    public String albumArtist;

    // 🚀 [iPod 스타일] Composers 메뉴 그룹핑용 (COMPOSER/TCOM 태그, 없으면 "Unknown Composer")
    public String composer;

    // 🚀 [크래시 버그 수정] artist/album은 그동안 null 방어가 전혀 없었습니다 - 태그 추출이 실패해서
    // null이 들어오면, 나중에 song.artist.equals(...)나 song.album.equals(...)를 부르는 모든 곳
    // (아티스트/앨범 분류, 검색 등)에서 NullPointerException으로 런처가 죽었습니다. year/genre/
    // albumArtist/composer처럼 안전하게 "Unknown X"로 대체합니다.
    private static String safe(String value, String fallback) {
        return (value != null && !value.trim().isEmpty()) ? value : fallback;
    }

    // 💡 기존 코드 호환성을 위한 기본 생성자 (M3U 등에서 오류가 나지 않게 방어해 줍니다)
    public SongItem(File file, String title, String artist, String album) {
        this.file = file;
        this.title = title;
        this.artist = safe(artist, "Unknown Artist");
        this.album = safe(album, "Unknown Album");
        this.year = "Unknown Year";
        this.genre = "Unknown Genre";
        this.albumArtist = this.artist;
        this.composer = "Unknown Composer";
    }

    // 🚀 [신규 엔진] 연도와 장르까지 꽉 채워서 담아주는 진화된 생성자 추가!
    public SongItem(File file, String title, String artist, String album, String year, String genre) {
        this.file = file;
        this.title = title;
        this.artist = safe(artist, "Unknown Artist");
        this.album = safe(album, "Unknown Album");
        // 값이 비어있으면(null) 자동으로 'Unknown' 꼬리표를 달아줍니다.
        this.year = safe(year, "Unknown Year");
        this.genre = safe(genre, "Unknown Genre");
        this.albumArtist = this.artist;
        this.composer = "Unknown Composer";
    }

    // 🚀 [신규 추가] ALBUMARTIST 태그까지 담는 완전판 생성자
    public SongItem(File file, String title, String artist, String album, String year, String genre, String albumArtist) {
        this.file = file;
        this.title = title;
        this.artist = safe(artist, "Unknown Artist");
        this.album = safe(album, "Unknown Album");
        this.year = safe(year, "Unknown Year");
        this.genre = safe(genre, "Unknown Genre");
        this.albumArtist = safe(albumArtist, this.artist);
        this.composer = "Unknown Composer";
    }

    // 🚀 [iPod 스타일] COMPOSER 태그까지 담는 최종판 생성자
    public SongItem(File file, String title, String artist, String album, String year, String genre, String albumArtist, String composer) {
        this.file = file;
        this.title = title;
        this.artist = safe(artist, "Unknown Artist");
        this.album = safe(album, "Unknown Album");
        this.year = safe(year, "Unknown Year");
        this.genre = safe(genre, "Unknown Genre");
        this.albumArtist = safe(albumArtist, this.artist);
        this.composer = safe(composer, "Unknown Composer");
    }
}