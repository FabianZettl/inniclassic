package com.themoon.y1;

import android.content.Context;
import com.themoon.y1.io.SafeFiles;
import android.net.wifi.WifiManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Y1WebServer extends Thread {
    private volatile ServerSocket serverSocket;
    private final java.util.Set<Socket> clients = java.util.Collections.synchronizedSet(new java.util.HashSet<Socket>());
    private final java.util.concurrent.ThreadPoolExecutor workers = new java.util.concurrent.ThreadPoolExecutor(
            2, 4, 30, TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(8));
    private volatile boolean running = true;
    private final File rootFolder;
    private final int port;
    private Context context;

    public Y1WebServer(Context context, File rootFolder) {
        this(context, rootFolder, 8080);
    }

    Y1WebServer(Context context, File rootFolder, int port) {
        this.context = context;
        this.rootFolder = rootFolder.getAbsoluteFile();
        this.port = port;
    }

    int getListeningPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                Socket socket = serverSocket.accept();
                clients.add(socket);
                try { workers.execute(new RequestHandler(socket)); }
                catch (java.util.concurrent.RejectedExecutionException e) {
                    clients.remove(socket);
                    socket.close();
                }
            }
        } catch (Exception e) {}
        finally { stopServer(); }
    }

    public void stopServer() {
        running = false;
        workers.shutdownNow();
        synchronized (clients) {
            for (Socket client : clients) { try { client.close(); } catch (Exception ignored) {} }
            clients.clear();
        }
        try { if (serverSocket != null) serverSocket.close(); } catch(Exception e){}
    }

    public String getLocalIpAddress() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wm.getConnectionInfo().getIpAddress();
            return String.format(Locale.US, "%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        } catch (Exception ex) { return "Unknown IP"; }
    }

    private void deleteFileOrFolder(File file) throws java.io.IOException {
        File base = rootFolder.getCanonicalFile();
        File canonical = file.getCanonicalFile();
        if (canonical.equals(base) || !canonical.getPath().startsWith(base.getPath() + File.separator))
            throw new java.io.IOException("Cannot delete outside shared folder");
        // Never recurse into symbolic links, including links back to an ancestor.
        if (!file.getAbsoluteFile().equals(canonical)) throw new java.io.IOException("Cannot delete symbolic link");
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new java.io.IOException("Cannot read folder");
            for (File child : children) deleteFileOrFolder(child);
        }
        if (!file.delete()) throw new java.io.IOException("Cannot delete file");
    }

    private class RequestHandler implements Runnable {
        private Socket socket;
        public RequestHandler(Socket socket) { this.socket = socket; }

        private String readHeaderLine(InputStream is) throws java.io.IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = is.read()) != -1) {
                if (c == '\r') continue;
                if (c == '\n') break;
                if (sb.length() >= 8192) throw new java.io.IOException("Header too long");
                sb.append((char) c);
            }
            return sb.toString();
        }

        private String readBody(InputStream is, int contentLength) throws java.io.IOException {
            if (contentLength < 0 || contentLength > 65536) throw new java.io.IOException("Body too large");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int totalRead = 0, bytesRead;
            while (totalRead < contentLength && (bytesRead = is.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead))) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            if (totalRead != contentLength) throw new java.io.EOFException("Incomplete body");
            return new String(bos.toByteArray(), "UTF-8");
        }

        private String formParam(String body, String key) throws java.io.IOException {
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                if (pair.substring(0, eq).equals(key)) {
                    return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                }
            }
            return "";
        }

        private void writeJson(OutputStream os, String json) throws java.io.IOException {
            os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + json).getBytes("UTF-8"));
        }

        public void run() {
            try {
                socket.setSoTimeout(15000);
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                String requestLine = readHeaderLine(is);
                if (requestLine == null || requestLine.isEmpty()) return;

                String[] parts = requestLine.split(" ");
                if (parts.length != 3) throw new java.io.IOException("Invalid request");
                String method = parts[0];
                String path = parts[1];

                int contentLength = 0;
                String line;
                int headerCount = 0;
                String origin = null, host = null;
                while (!(line = readHeaderLine(is)).isEmpty()) {
                    if (++headerCount > 100) throw new java.io.IOException("Too many headers");
                    if (line.toLowerCase(Locale.US).startsWith("host:")) host = line.substring(5).trim();
                    if (line.toLowerCase(Locale.US).startsWith("origin:")) origin = line.substring(7).trim();
                    if (line.toLowerCase(Locale.US).startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.split(":")[1].trim());
                    }
                }

                if ("POST".equals(method) && origin != null && !origin.equals("http://" + host))
                    throw new java.io.IOException("Cross-origin write rejected");
                if (contentLength < 0) throw new java.io.IOException("Invalid length");

                // 1️⃣ 화면 UI 전송 (프론트엔드 - 인라인 플레이어 + 🚀 텍스트 에디터 탑재 + 🚀 드래그 앤 드롭 지원)
                if (method.equals("GET") && path.equals("/")) {
                    String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>" +
                            "<title>Y1 File Manager</title><style>" +
                            // 🚀 [디자인 수정] 바탕색 및 기본 글꼴 (Material Dark Theme 기반)
                            "body{font-family:'Roboto', 'Segoe UI', sans-serif; background:#1E1E24; color:#E0E0E0; padding:20px; text-align:center; max-width:800px; margin:0 auto; padding-bottom:120px;} " +
                            "input, select, button{font-size:14px; padding:10px 16px; margin:5px; border-radius:20px; border:none; outline:none; transition:0.2s;} " +
                            "input[type=text]{width:calc(100% - 120px); background:#2A2A35; color:#E0E0E0; border-radius:12px; padding:12px;} " +

                            // 🚀 [디자인 수정] 버튼 색상 (보라색 포인트 & 모던 톤)
                            "button{background:#B39DDB; color:#121212; font-weight:600; cursor:pointer;} " + // 기본 버튼 (연한 보라)
                            "button:hover{background:#9575CD;} " +
                            "button.danger{background:#424250; color:#E57373;} " + // 삭제/취소 버튼 (어두운 회색 바탕, 빨간 글씨)
                            "button.danger:hover{background:#EF5350; color:#fff;} " +
                            "button.action{background:#383848; color:#B39DDB;} " + // 서브 액션 버튼 (어두운 바탕, 보라색 글씨)
                            "button.action:hover{background:#454555;} " +

                            // 🚀 [디자인 수정] 박스 및 리스트 아이템
                            ".box{background:#252530; padding:20px; border-radius:16px; margin:15px 0; text-align:left; box-shadow:0 4px 6px rgba(0,0,0,0.3);} " +
                            ".item{display:flex; justify-content:space-between; align-items:center; padding:12px; border-bottom:1px solid #333340; cursor:pointer; transition:0.2s;} " +
                            ".item:last-child{border-bottom:none;} " +
                            ".item:hover{background:#2D2D3A; border-radius:8px;} " +
                            ".item-left{display:flex; align-items:center; flex-grow:1; overflow:hidden; gap:12px;} " +
                            ".item-name{white-space:nowrap; overflow:hidden; text-overflow:ellipsis; font-weight:500;} " +
                            ".thumb{width:40px; height:40px; object-fit:cover; border-radius:8px; background:#1A1A20;} " +
                            ".icon{font-size:22px; width:40px; text-align:center; color:#B39DDB;} " + // 아이콘도 연보라 톤으로 통일
                            ".btn-group{display:flex; gap:6px;} " +

                            // 오디오 플레이어
                            "#audioBox{position:fixed; bottom:0; left:0; right:0; background:#252530; border-top:1px solid #454555; padding:15px; display:none; z-index:100; box-shadow:0 -2px 10px rgba(0,0,0,0.5);} " +
                            "audio{width:100%; max-width:800px; margin:0 auto; display:block; outline:none; border-radius:24px;} " +

                            // 드래그 앤 드롭 애니메이션
                            "#uploadBox{transition:0.3s; border:2px dashed #454555;} " +
                            "#uploadBox.dragover{background:#2D2D3A; border:2px dashed #B39DDB;} " +

                            // 텍스트 에디터 모달
                            "#editorBox{position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(18,18,22,0.95); z-index:200; padding:20px; box-sizing:border-box; display:none;} " +
                            "#editorArea{width:100%; height:calc(100% - 120px); background:#1E1E24; color:#E0E0E0; font-family:'Courier New', monospace; font-size:15px; border:1px solid #454555; border-radius:12px; padding:15px; resize:none; box-shadow:inset 0 2px 5px rgba(0,0,0,0.3);} " +
                            "#editorTitle{color:#B39DDB; margin-top:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; font-weight:600;}" +
                            "</style></head><body>" +

                            "<h2 style='color:#E0E0E0; font-weight:600; letter-spacing:0.5px;'>📁 Y1 File Manager " +
                            "<a href='/lastfm' style='font-size:14px; color:#B39DDB; text-decoration:none; vertical-align:middle;'>🎧 Last.fm Settings</a></h2>" +

                            // 업로드/폴더생성 박스
                            "<div class='box' id='uploadBox'>" +
                            "<div style='font-size:16px; margin-bottom:12px; font-weight:500; color:#B39DDB;'>📍 <span id='currentPathText'>/</span></div>" +
                            "<div style='text-align:center; color:#9E9E9E; font-size:13px; margin-bottom:20px; padding-bottom:15px; border-bottom:1px solid #333340;'>💡 <b>Drag & Drop</b> files anywhere in this box to upload instantly!</div>" +
                            "<div style='display:flex; gap:8px; margin-bottom:15px;'>" +
                            "<input type='text' id='fName' placeholder='New folder name...'>" +
                            "<button onclick='createFolder()'>Create</button></div>" +
                            "<div style='display:flex; gap:8px; align-items:center;'>" +
                            "<input type='file' id='fInput' multiple accept='*/*' style='flex-grow:1; color:#9E9E9E;'>" +
                            "<button onclick='uploadAll()' class='action'>Upload Here</button></div>" +
                            "<div id='status' style='margin-top:12px; color:#81C784; font-weight:600; font-size:14px;'></div>" +
                            "</div>" +

                            // 파일 리스트 박스
                            "<div class='box' id='fileList'>Loading...</div>" +

                            // 플로팅 오디오 플레이어
                            "<div id='audioBox'>" +
                            "<div id='audioTitle' style='max-width:800px; margin:0 auto 12px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; color:#B39DDB;'></div>" +
                            "<audio id='audioPlayer' controls controlsList='nodownload'></audio>" +
                            "</div>" +

                            // 전체화면 텍스트 에디터
                            "<div id='editorBox'>" +
                            "<h3 id='editorTitle'>📝 Edit File</h3>" +
                            "<textarea id='editorArea' spellcheck='false' wrap='off'></textarea>" +
                            "<div style='display:flex; gap:12px; margin-top:15px;'>" +
                            "<button class='action' style='flex:1; background:#B39DDB; color:#121212;' onclick='saveFile()'>💾 Save Settings</button>" +
                            "<button class='danger' style='flex:1;' onclick='closeEditor()'>Cancel</button>" +
                            "</div></div>" +

                            "<script>" +
                            "const nativeFetch = window.fetch.bind(window); window.fetch = (url, options) => nativeFetch(url, options).then(r => { if (!r.ok) { document.getElementById('status').textContent = 'Request failed (' + r.status + ')'; throw Error('Request failed'); } return r; });" +
                            "let currentPath = '';" +
                            "function loadList() {" +
                            " fetch('/api/list?dir=' + encodeURIComponent(currentPath)).then(r=>{if(!r.ok) throw Error('Cannot load folder'); return r.json();}).then(data=>{" +
                            " document.getElementById('currentPathText').textContent='/' + currentPath;" +
                            " const list=document.getElementById('fileList'); list.textContent='';" +
                            " function button(row,label,action) {const b=document.createElement('button');b.className='action';b.textContent=label;b.onclick=e=>{e.stopPropagation();action(e);};row.appendChild(b);}" +
                            " if(currentPath) {const back=document.createElement('div');back.className='item';back.textContent='\u2190 Go Back';back.onclick=goUp;list.appendChild(back);}" +
                            " data.forEach(f=>{" +
                            " const row=document.createElement('div');row.className='item';" +
                            " const label=document.createElement('span');label.className='item-name';label.textContent=(f.isDir?'\ud83d\udcc1 ':'')+f.name;row.appendChild(label);" +
                            " const path=encodeURIComponent(currentPath?currentPath+'/'+f.name:f.name);" +
                            " const ext=f.name.split('.').pop().toLowerCase();" +
                            " if(f.isDir) row.onclick=()=>goInto(f.name);" +
                            " else {" +
                            " if(['jpg','jpeg','png','webp','gif'].includes(ext)) {const image=document.createElement('img');image.className='thumb';image.src='/api/file?path='+path;image.loading='lazy';row.insertBefore(image,label);row.onclick=()=>window.open('/api/file?path='+path,'_blank');}" +
                            " if(['mp3','flac','wav','ogg','opus','m4a','aac'].includes(ext)) row.onclick=()=>playAudio(path,f.name);" +
                            " if(['json','txt','xml','ini','md','m3u','m3u8','eq'].includes(ext)) button(row,'Edit',e=>openEditor(e,path,f.name));" +
                            " button(row,'Download',()=>{window.location.href='/api/download?path='+path;});" +
                            " }" +
                            " button(row,'Rename',e=>renameItem(e,f.name));" +
                            " button(row,'Delete',e=>deleteItem(e,f.name));" +
                            " list.appendChild(row);" +
                            " });" +
                            " }).catch(e=>{document.getElementById('status').textContent=e.message;});" +
                            "}" +
                            "function goInto(dirName) { currentPath = currentPath ? currentPath + '/' + dirName : dirName; loadList(); }" +
                            "function goUp() { let parts = currentPath.split('/'); parts.pop(); currentPath = parts.join('/'); loadList(); }" +

                            "function playAudio(path, name) {" +
                            "  document.getElementById('audioBox').style.display = 'block';" +
                            "  document.getElementById('audioTitle').innerText = '▶ ' + name;" +
                            "  let player = document.getElementById('audioPlayer');" +
                            "  player.src = '/api/file?path=' + path;" +
                            "  player.play();" +
                            "}" +

                            "let editingPath = '';" +
                            "function openEditor(e, path, name) {" +
                            "  if(e) e.stopPropagation();" +
                            "  editingPath = path;" +
                            "  document.getElementById('editorTitle').innerText = '📝 Editing: ' + name;" +
                            "  document.getElementById('editorArea').value = 'Loading content...';" +
                            "  document.getElementById('editorBox').style.display = 'block';" +
                            "  fetch('/api/file?path=' + path).then(r => r.text()).then(txt => {" +
                            "    document.getElementById('editorArea').value = txt;" +
                            "  });" +
                            "}" +
                            "function closeEditor() { document.getElementById('editorBox').style.display = 'none'; editingPath=''; }" +
                            "function saveFile() {" +
                            "  let content = document.getElementById('editorArea').value;" +
                            "  fetch('/api/save?path=' + editingPath, { method: 'POST', body: content }).then(() => {" +
                            "    alert('✅ File saved successfully!'); closeEditor();" +
                            "  }).catch(e => alert('Failed to save.'));" +
                            "}" +

                            "function createFolder() { " +
                            "  var n = document.getElementById('fName').value; if(!n) return;" +
                            "  fetch('/api/create?dir=' + encodeURIComponent(currentPath) + '&name=' + encodeURIComponent(n), {method:'POST'}).then(()=>{ document.getElementById('fName').value=''; loadList();});" +
                            "}" +
                            "function renameItem(e, oldName) { " +
                            "  e.stopPropagation();" +
                            "  var newName = prompt('Enter new name for: ' + oldName, oldName);" +
                            "  if(!newName || newName === oldName) return;" +
                            "  fetch('/api/rename?dir=' + encodeURIComponent(currentPath) + '&old=' + encodeURIComponent(oldName) + '&new=' + encodeURIComponent(newName), {method:'POST'}).then(()=>loadList());" +
                            "}" +
                            "function deleteItem(e, name) { " +
                            "  e.stopPropagation();" +
                            "  if(!confirm('Delete ' + name + '?')) return;" +
                            "  fetch('/api/delete?path=' + encodeURIComponent(currentPath ? currentPath + '/' + name : name), {method:'POST'}).then(()=>loadList());" +
                            "}" +

                            "async function uploadAll(droppedFiles) { " +
                            "  var files = droppedFiles || document.getElementById('fInput').files; var st = document.getElementById('status'); " +
                            "  if(files.length === 0) return;" +
                            "  for(var i=0; i<files.length; i++) { " +
                            "    st.innerText = 'Uploading: ' + files[i].name + ' (' + (i+1) + '/' + files.length + ')'; " +
                            "    await fetch('/api/upload?dir=' + encodeURIComponent(currentPath) + '&name=' + encodeURIComponent(files[i].name), {method:'POST', body:files[i]}); " +
                            "  } " +
                            "  st.innerText = '✅ Upload Complete!'; document.getElementById('fInput').value=''; loadList();" +
                            "}" +

                            "async function uploadFolderItems(fileList) { " +
                            "  var st = document.getElementById('status'); " +
                            "  for(var i=0; i<fileList.length; i++) { " +
                            "    let item = fileList[i]; " +
                            "    let displayPath = item.path ? item.path + item.file.name : item.file.name; " +
                            "    st.innerText = 'Uploading: ' + displayPath + ' (' + (i+1) + '/' + fileList.length + ')'; " +
                            "    let targetDir = currentPath; " +
                            "    if(item.path) { " +
                            "       let subDir = item.path.replace(/\\/$/, ''); " +
                            "       targetDir = currentPath ? currentPath + '/' + subDir : subDir; " +
                            "    } " +
                            "    await fetch('/api/upload?dir=' + encodeURIComponent(targetDir) + '&name=' + encodeURIComponent(item.file.name), {method:'POST', body:item.file}); " +
                            "  } " +
                            "  st.innerText = '✅ Folder Upload Complete!'; loadList();" +
                            "}" +

                            "let dropZone = document.getElementById('uploadBox');" +
                            "dropZone.addEventListener('dragover', function(e) { e.preventDefault(); dropZone.classList.add('dragover'); });" +
                            "dropZone.addEventListener('dragleave', function(e) { e.preventDefault(); dropZone.classList.remove('dragover'); });" +
                            "dropZone.addEventListener('drop', function(e) { " +
                            "  e.preventDefault(); dropZone.classList.remove('dragover'); " +
                            "  let items = e.dataTransfer.items; " +
                            "  if(!items) { if(e.dataTransfer.files.length > 0) uploadAll(e.dataTransfer.files); return; } " +
                            "  " +
                            "  document.getElementById('status').innerText = 'Scanning dropped items...'; " +
                            "  let filesToUpload = []; " +
                            "  let pending = 0; " +
                            "  " +
                            "  function scanEntry(item, path) { " +
                            "    if(item.isFile) { " +
                            "      pending++; " +
                            "      item.file(f => { filesToUpload.push({file: f, path: path}); pending--; checkDone(); }); " +
                            "    } else if(item.isDirectory) { " +
                            "      let dirReader = item.createReader(); " +
                            "      pending++; " +
                            "      function readBatch() { dirReader.readEntries(entries => { " +
                            "        if(!entries.length) { pending--; checkDone(); return; } " +
                            "        entries.forEach(entry => scanEntry(entry, path + item.name + '/')); " +
                            "        readBatch(); " +
                            "      }, () => { pending--; document.getElementById('status').textContent='Cannot read dropped folder'; }); } readBatch(); " +
                            "    } " +
                            "  } " +
                            "  function checkDone() { " +
                            "    if(pending === 0) { " +
                            "      if(filesToUpload.length > 0) uploadFolderItems(filesToUpload); " +
                            "      else document.getElementById('status').innerText = 'No files found.'; " +
                            "    } " +
                            "  } " +
                            "  for(let i=0; i<items.length; i++) { " +
                            "    let entry = items[i].webkitGetAsEntry(); " +
                            "    if(entry) scanEntry(entry, ''); " +
                            "  } " +
                            "});" +

                            "window.onload = loadList;" +
                            "</script></body></html>";

                    os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n" + html).getBytes("UTF-8"));
                }
                // 2️⃣ [API] 파일 및 폴더 리스트 응답 (JSON 형식)
                else if (method.equals("GET") && path.startsWith("/api/list")) {
                    String q = path.contains("?") ? path.split("\\?")[1] : "";
                    String dirStr = "";
                    if (q.startsWith("dir=")) dirStr = URLDecoder.decode(q.substring(4), "UTF-8");

                    File targetDir = dirStr.isEmpty() ? rootFolder : SafeFiles.resolve(rootFolder, dirStr);
                    StringBuilder json = new StringBuilder("[");

                    if (targetDir.exists() && targetDir.isDirectory()) {
                        File[] files = targetDir.listFiles();
                        if (files != null) {
                            for (int i=0; i<2; i++) {
                                for (File f : files) {
                                    boolean isDir = f.isDirectory();
                                    if ((i == 0 && isDir) || (i == 1 && !isDir)) {
                                        if (json.length() > 1) json.append(",");
                                        json.append("{\"name\":").append(org.json.JSONObject.quote(f.getName())).append(",\"isDir\":").append(isDir).append("}");
                                    }
                                }
                            }
                        }
                    }
                    json.append("]");
                    os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + json.toString()).getBytes("UTF-8"));
                }

                // 3️⃣ [API] 폴더 생성
                else if (method.equals("POST") && path.startsWith("/api/create")) {
                    String q = path.split("\\?")[1];
                    String[] params = q.split("&");
                    String dirStr = "", name = "";
                    for (String p : params) {
                        if (p.startsWith("dir=")) dirStr = URLDecoder.decode(p.substring(4), "UTF-8");
                        if (p.startsWith("name=")) name = URLDecoder.decode(p.substring(5), "UTF-8");
                    }
                    File targetDir = dirStr.isEmpty() ? rootFolder : SafeFiles.resolve(rootFolder, dirStr);
                    File newDir = SafeFiles.child(targetDir, name);
                    if (!newDir.isDirectory() && !newDir.mkdirs()) throw new java.io.IOException("Cannot create folder");
                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }

                // 4️⃣ [API] 파일 및 폴더 삭제
                else if (method.equals("POST") && path.startsWith("/api/delete")) {
                    String q = path.split("\\?")[1];
                    String targetPath = URLDecoder.decode(q.substring(5), "UTF-8");
                    File targetFile = SafeFiles.resolve(rootFolder, targetPath);
                    if (targetFile.equals(rootFolder.getCanonicalFile())) throw new java.io.IOException("Cannot delete root");
                    if (targetFile.exists()) {
                        deleteFileOrFolder(targetFile);
                    }
                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }

                // 🚀 [수정 5] [API] 파일 및 폴더 이름 변경 (Rename) 엔진 장착!
                else if (method.equals("POST") && path.startsWith("/api/rename")) {
                    String q = path.split("\\?")[1];
                    String[] params = q.split("&");
                    String dirStr = "", oldName = "", newName = "";
                    for (String p : params) {
                        if (p.startsWith("dir=")) dirStr = URLDecoder.decode(p.substring(4), "UTF-8");
                        if (p.startsWith("old=")) oldName = URLDecoder.decode(p.substring(4), "UTF-8");
                        if (p.startsWith("new=")) newName = URLDecoder.decode(p.substring(4), "UTF-8");
                    }

                    File targetDir = dirStr.isEmpty() ? rootFolder : SafeFiles.resolve(rootFolder, dirStr);
                    File oldFile = SafeFiles.child(targetDir, oldName);
                    File newFile = SafeFiles.child(targetDir, newName);

                    // 기존 파일이 존재하고 새 이름의 파일이 없을 때만 안전하게 이름 변경 실행
                    if (!oldFile.exists() || newFile.exists()) throw new java.io.IOException("Invalid rename");
                    if (oldFile.exists() && !newFile.exists()) {
                        if (!oldFile.renameTo(newFile)) throw new java.io.IOException("Cannot rename file");
                    }
                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }

                // 5️⃣ [API] 파일 읽기 (스트리밍, 다운로드, 코드 불러오기)

                // 5️⃣ [API] 파일 읽기 (스트리밍, 다운로드, 코드 불러오기)
                else if (method.equals("GET") && path.startsWith("/api/file")) {
                    String q = path.split("\\?")[1];
                    String targetPath = URLDecoder.decode(q.substring(5), "UTF-8");
                    File targetFile = SafeFiles.resolve(rootFolder, targetPath);

                    if (!targetFile.exists() || targetFile.isDirectory()) {
                        os.write("HTTP/1.1 404 Not Found\r\n\r\nNot Found".getBytes("UTF-8"));
                    } else {
                        String mimeType = "application/octet-stream";
                        String lowerName = targetFile.getName().toLowerCase();
                        if (lowerName.endsWith(".mp3")) mimeType = "audio/mpeg";
                        else if (lowerName.endsWith(".flac")) mimeType = "audio/flac";
                        else if (lowerName.endsWith(".wav")) mimeType = "audio/wav";
                        else if (lowerName.endsWith(".ogg")) mimeType = "audio/ogg";
                        else if (lowerName.endsWith(".m4a") || lowerName.endsWith(".aac")) mimeType = "audio/mp4";
                        else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) mimeType = "image/jpeg";
                        else if (lowerName.endsWith(".png")) mimeType = "image/png";
                        else if (lowerName.endsWith(".json")) mimeType = "application/json";
                            // 🚀 [수정 2] 방금 추가한 파일들도 브라우저가 순수한 '글자'로 인식해서 에디터 창에 띄우도록 명시!
                        else if (lowerName.endsWith(".txt") || lowerName.endsWith(".m3u") || lowerName.endsWith(".m3u8") || lowerName.endsWith(".eq")) mimeType = "text/plain";

                        String header = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + targetFile.length() + "\r\n" +
                                "Connection: close\r\n\r\n";
                        os.write(header.getBytes("UTF-8"));

                        try (FileInputStream fis = new FileInputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        }
                    }
                }

                // 6️⃣ [API] 파일 업로드
                else if (method.equals("POST") && path.startsWith("/api/upload")) {
                    String q = path.split("\\?")[1];
                    String[] params = q.split("&");
                    String dirStr = "", name = "unnamed.file";
                    for (String p : params) {
                        if (p.startsWith("dir=")) dirStr = URLDecoder.decode(p.substring(4), "UTF-8");
                        if (p.startsWith("name=")) name = URLDecoder.decode(p.substring(5), "UTF-8");
                    }

                    File targetDir = dirStr.isEmpty() ? rootFolder : SafeFiles.resolve(rootFolder, dirStr);
                    if (!targetDir.isDirectory() && !targetDir.mkdirs()) throw new java.io.IOException("Cannot create folder");
                    File outFile = SafeFiles.child(targetDir, name);

                    SafeFiles.replace(outFile, is, contentLength);

                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }

                // 7️⃣ [API] 🚀 텍스트 파일 저장 (코드 에디터에서 전송된 텍스트를 기기에 덮어쓰기)
                else if (method.equals("POST") && path.startsWith("/api/save")) {
                    String q = path.split("\\?")[1];
                    String targetPath = URLDecoder.decode(q.substring(5), "UTF-8");
                    File targetFile = SafeFiles.resolve(rootFolder, targetPath);

                    SafeFiles.replace(targetFile, is, contentLength);

                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.startsWith("/api/download")) {
                    String q = path.split("\\?")[1];
                    String targetPath = URLDecoder.decode(q.substring(5), "UTF-8");
                    File targetFile = SafeFiles.resolve(rootFolder, targetPath);

                    if (!targetFile.exists() || targetFile.isDirectory()) {
                        os.write("HTTP/1.1 404 Not Found\r\n\r\nNot Found".getBytes("UTF-8"));
                    } else {
                        // 💡 브라우저가 화면에 재생하지 않고 "무조건 파일로 저장"하게 만드는 Content-Disposition 헤더!
                        String header = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/octet-stream\r\n" +
                                "Content-Disposition: attachment; filename=\"" + targetFile.getName().replaceAll("[\\r\\n\"\\\\]", "_") + "\"\r\n" +
                                "Content-Length: " + targetFile.length() + "\r\n" +
                                "Connection: close\r\n\r\n";
                        os.write(header.getBytes("UTF-8"));

                        try (FileInputStream fis = new FileInputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        }
                    }
                }
                // 8️⃣ [Last.fm] 로그인 페이지 (PC 브라우저에서 편하게 아이디/비번 입력용)
                else if (method.equals("GET") && path.equals("/lastfm")) {
                    String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>" +
                            "<title>Y1 Last.fm Settings</title><style>" +
                            "body{font-family:'Roboto','Segoe UI',sans-serif; background:#1E1E24; color:#E0E0E0; padding:20px; max-width:480px; margin:0 auto;} " +
                            "a{color:#B39DDB;} " +
                            "input{width:100%; box-sizing:border-box; font-size:15px; padding:12px; margin:6px 0; border-radius:12px; border:none; background:#2A2A35; color:#E0E0E0; outline:none;} " +
                            "button{width:100%; font-size:15px; padding:12px; margin:6px 0; border-radius:20px; border:none; background:#B39DDB; color:#121212; font-weight:600; cursor:pointer;} " +
                            "button:hover{background:#9575CD;} " +
                            "button.danger{background:#424250; color:#E57373;} " +
                            ".box{background:#252530; padding:20px; border-radius:16px; margin:15px 0;} " +
                            "#msg{margin-top:10px; font-weight:600;}" +
                            "</style></head><body>" +
                            "<h2>🎧 Last.fm Scrobbling</h2>" +
                            "<p><a href='/'>&larr; Back to File Manager</a></p>" +
                            "<div class='box' id='statusBox'>Loading...</div>" +
                            "<div class='box' id='loginBox' style='display:none;'>" +
                            "<input id='u' type='text' placeholder='Last.fm username' autocapitalize='off' autocorrect='off'>" +
                            "<input id='p' type='password' placeholder='Last.fm password'>" +
                            "<button onclick='doLogin()'>Log In</button>" +
                            "<div id='msg'></div>" +
                            "</div>" +
                            "<script>" +
                            "function refreshStatus() {" +
                            "  fetch('/api/lastfm/status').then(r=>r.json()).then(s => {" +
                            "    let box = document.getElementById('statusBox');" +
                            "    if (s.loggedIn) {" +
                            "      box.innerHTML = `<p>Logged in as <b id='lastfmUsername'></b></p>` +" +
                            "        `<button onclick='setEnabled(${!s.enabled})'>Turn Scrobbling ${s.enabled ? 'OFF' : 'ON'}</button>` +" +
                            "        `<p>Scrobbling is currently <b>${s.enabled ? 'ON' : 'OFF'}</b></p>` +" +
                            "        `<button class='danger' onclick='doLogout()'>Log Out</button>`;" +
                            "      document.getElementById('lastfmUsername').textContent = '@' + s.username;" +
                            "      document.getElementById('loginBox').style.display = 'none';" +
                            "    } else {" +
                            "      box.innerHTML = '<p>Not logged in to Last.fm.</p>';" +
                            "      document.getElementById('loginBox').style.display = 'block';" +
                            "    }" +
                            "  });" +
                            "}" +
                            "function doLogin() {" +
                            "  let u = document.getElementById('u').value.trim();" +
                            "  let p = document.getElementById('p').value;" +
                            "  let msg = document.getElementById('msg');" +
                            "  if (!u || !p) { msg.style.color='#E57373'; msg.innerText='Please fill in both fields.'; return; }" +
                            "  msg.style.color='#B39DDB'; msg.innerText='Logging in...';" +
                            "  fetch('/api/lastfm/login', {method:'POST', body:'username='+encodeURIComponent(u)+'&password='+encodeURIComponent(p)})" +
                            "    .then(r=>r.json()).then(res => {" +
                            "      if (res.success) { msg.style.color='#81C784'; msg.innerText='✅ Logged in as @' + res.message; refreshStatus(); }" +
                            "      else { msg.style.color='#E57373'; msg.innerText='❌ ' + res.message; }" +
                            "    });" +
                            "}" +
                            "function doLogout() { fetch('/api/lastfm/logout', {method:'POST'}).then(refreshStatus); }" +
                            "function setEnabled(v) { fetch('/api/lastfm/enabled?value=' + v, {method:'POST'}).then(refreshStatus); }" +
                            "refreshStatus();" +
                            "</script></body></html>";
                    os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n" + html).getBytes("UTF-8"));
                }
                // [API] Last.fm 로그인 상태 조회
                else if (method.equals("GET") && path.startsWith("/api/lastfm/status")) {
                    com.themoon.y1.managers.LastFmScrobbler s = com.themoon.y1.managers.LastFmScrobbler.getInstance(context);
                    String json = "{\"loggedIn\":" + s.isLoggedIn() + ",\"username\":\"" + s.getUsername().replace("\"", "\\\"") +
                            "\",\"enabled\":" + s.isEnabled() + "}";
                    writeJson(os, json);
                }
                // [API] Last.fm 로그인 (아이디/비번은 PC 브라우저에서 입력받아 이 엔드포인트로 전송됨)
                else if (method.equals("POST") && path.startsWith("/api/lastfm/login")) {
                    String body = readBody(is, contentLength);
                    String username = formParam(body, "username");
                    String password = formParam(body, "password");

                    final CountDownLatch latch = new CountDownLatch(1);
                    final AtomicBoolean success = new AtomicBoolean(false);
                    final AtomicReference<String> message = new AtomicReference<>("Login failed");

                    com.themoon.y1.managers.LastFmScrobbler.getInstance(context).login(username, password,
                            new com.themoon.y1.managers.LastFmScrobbler.LoginCallback() {
                                @Override
                                public void onResult(boolean ok, String msg) {
                                    success.set(ok);
                                    message.set(msg);
                                    latch.countDown();
                                }
                            });
                    latch.await(15, TimeUnit.SECONDS);

                    String json = "{\"success\":" + success.get() + ",\"message\":\"" +
                            String.valueOf(message.get()).replace("\"", "\\\"") + "\"}";
                    writeJson(os, json);
                }
                // [API] Last.fm 로그아웃
                else if (method.equals("POST") && path.startsWith("/api/lastfm/logout")) {
                    com.themoon.y1.managers.LastFmScrobbler.getInstance(context).logout();
                    writeJson(os, "{\"ok\":true}");
                }
                // [API] 스크로블링 ON/OFF 토글
                else if (method.equals("POST") && path.startsWith("/api/lastfm/enabled")) {
                    String q = path.contains("?") ? path.split("\\?")[1] : "";
                    boolean value = q.contains("value=true");
                    com.themoon.y1.managers.LastFmScrobbler.getInstance(context).setEnabled(value);
                    writeJson(os, "{\"ok\":true}");
                }
                else { os.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\nNot Found".getBytes("UTF-8")); }
                os.flush();
            } catch (Exception e) {
                try { socket.getOutputStream().write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\nRequest failed".getBytes("UTF-8")); } catch (Exception ignored) {}
            }
            finally {
                clients.remove(socket);
                try { socket.close(); } catch (Exception e) {}
            }
        }
    }
}