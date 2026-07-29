/* ============================================================================
   공통 첨부 picker — fragments/file-picker.html 의 동작.
   ----------------------------------------------------------------------------
   · 파일 단위 병렬 업로드. 한 요청에 묶으면 1개 거부가 전체를 실패시킨다.
   · 여기서 하는 개수·크기 검사는 사용자 편의이지 방어가 아니다 — 서버가 다시 센다.
   · CSRF 토큰은 폼의 hidden 에서 읽는다(쿠키가 아니라 폼이 원천인 설정).
   · htmx:load 에서도 다시 돌 수 있게 data-initialized 가드(멱등).
   ========================================================================== */
(function () {
    'use strict';

    var UPLOAD_URL = '/api/v1/file/upload';

    function csrf(root) {
        var input = (root.closest('form') || document).querySelector('input[name="_csrf"]');
        return input ? input.value : null;
    }

    function humanSize(bytes) {
        if (bytes < 1024) { return bytes + ' B'; }
        if (bytes < 1048576) { return (bytes / 1024).toFixed(1) + ' KB'; }
        return (bytes / 1048576).toFixed(1) + ' MB';
    }

    function showError(root, message) {
        var box = root.querySelector('.gp-picker-error');
        if (!box) { return; }
        box.textContent = message;
        box.hidden = false;
    }

    function clearError(root) {
        var box = root.querySelector('.gp-picker-error');
        if (box) { box.hidden = true; }
    }

    /** 남아 있는 항목의 fileId 를 hidden 에 CSV 로 직렬화 — 폼이 보는 유일한 값. */
    function sync(root) {
        var ids = [];
        root.querySelectorAll('.gp-picker-item[data-file-id]').forEach(function (li) {
            ids.push(li.dataset.fileId);
        });
        var hidden = root.querySelector('.gp-picker-value');
        if (hidden) { hidden.value = ids.join(','); }
    }

    function countDone(root) {
        return root.querySelectorAll('.gp-picker-item[data-file-id]').length;
    }

    function makeRow(root, file) {
        var li = document.createElement('li');
        li.className = 'gp-picker-item gp-picker-uploading';

        var thumb = document.createElement('span');
        thumb.className = 'gp-picker-thumb';
        thumb.setAttribute('aria-hidden', 'true');
        var ext = document.createElement('span');
        ext.className = 'gp-picker-ext text-label-xs';
        var dot = file.name.lastIndexOf('.');
        ext.textContent = dot >= 0 ? file.name.substring(dot + 1).toUpperCase() : '?';
        thumb.appendChild(ext);

        var meta = document.createElement('span');
        meta.className = 'gp-picker-meta';
        var name = document.createElement('span');
        name.className = 'gp-picker-name text-body-sm';
        name.textContent = file.name;
        var size = document.createElement('span');
        size.className = 'gp-picker-size text-body-xs';
        size.textContent = humanSize(file.size);
        meta.appendChild(name);
        meta.appendChild(size);

        var bar = document.createElement('span');
        bar.className = 'gp-picker-bar';
        var fill = document.createElement('span');
        fill.className = 'gp-picker-bar-fill';
        bar.appendChild(fill);
        meta.appendChild(bar);

        var remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'gp-picker-remove krds-btn krds-btn-text krds-btn-sm';
        remove.dataset.action = 'picker-remove';
        remove.setAttribute('aria-label', '첨부 제거: ' + file.name);
        remove.textContent = '✕';

        li.appendChild(thumb);
        li.appendChild(meta);
        li.appendChild(remove);
        root.querySelector('.gp-picker-list').appendChild(li);
        return { li: li, fill: fill, thumb: thumb, name: name };
    }

    function upload(root, file, row) {
        var form = new FormData();
        form.append('file', file);
        form.append('entityType', root.dataset.entityType || 'ETC');
        form.append('entityId', root.dataset.entityId || '');
        form.append('category', root.dataset.category || 'ANY');
        // downloadAuth 는 보내지 않는다 — 공개 범위는 서버가 정한다(폼 저장 시점).
        if (root.dataset.siteId) { form.append('siteId', root.dataset.siteId); }

        var xhr = new XMLHttpRequest();
        xhr.open('POST', UPLOAD_URL, true);
        var token = csrf(root);
        if (token) { xhr.setRequestHeader('X-CSRF-TOKEN', token); }

        xhr.upload.onprogress = function (e) {
            if (e.lengthComputable) {
                row.fill.style.width = Math.round((e.loaded / e.total) * 100) + '%';
            }
        };
        xhr.onload = function () {
            var body = {};
            try { body = JSON.parse(xhr.responseText || '{}'); } catch (e) { body = {}; }
            if (xhr.status >= 200 && xhr.status < 300 && body.fileId) {
                row.li.classList.remove('gp-picker-uploading');
                row.li.dataset.fileId = body.fileId;
                row.fill.parentNode.remove();
                if (body.thumbUrl) {
                    var img = document.createElement('img');
                    img.src = body.thumbUrl;
                    img.alt = '';
                    row.thumb.innerHTML = '';
                    row.thumb.appendChild(img);
                }
                var link = document.createElement('a');
                link.className = 'gp-picker-name text-body-sm krds-link';
                link.href = body.url;
                link.textContent = body.originalName;
                row.name.replaceWith(link);
                sync(root);
            } else {
                row.li.classList.add('gp-picker-failed');
                row.fill.style.width = '100%';
                showError(root, (body.message || '업로드에 실패했습니다.') + ' (' + file.name + ')');
                // 실패한 행은 남겨 둔다 — 무엇이 왜 실패했는지 사용자가 알아야 다시 시도한다
                row.li.querySelector('.gp-picker-size').textContent =
                    body.message || '업로드 실패';
            }
        };
        xhr.onerror = function () {
            row.li.classList.add('gp-picker-failed');
            showError(root, '네트워크 오류로 업로드하지 못했습니다. (' + file.name + ')');
        };
        xhr.send(form);
    }

    function accept(root, fileList) {
        clearError(root);
        var max = parseInt(root.dataset.maxFiles || '20', 10);
        var files = Array.prototype.slice.call(fileList);
        var room = max - countDone(root) - root.querySelectorAll('.gp-picker-uploading').length;
        if (room <= 0) {
            showError(root, '최대 ' + max + '개까지 첨부할 수 있습니다.');
            return;
        }
        if (files.length > room) {
            showError(root, '최대 ' + max + '개까지 첨부할 수 있어 ' + room + '개만 올립니다.');
            files = files.slice(0, room);
        }
        files.forEach(function (f) {
            upload(root, f, makeRow(root, f));
        });
    }

    function init(root) {
        if (root.dataset.initialized === 'true') { return; }
        root.dataset.initialized = 'true';

        var input = root.querySelector('.gp-picker-input');
        var drop = root.querySelector('.gp-picker-drop');

        input.addEventListener('change', function () {
            accept(root, input.files);
            input.value = '';              // 같은 파일을 다시 고를 수 있게 비운다
        });

        ['dragenter', 'dragover'].forEach(function (type) {
            drop.addEventListener(type, function (e) {
                e.preventDefault();
                drop.classList.add('gp-picker-over');
            });
        });
        ['dragleave', 'drop'].forEach(function (type) {
            drop.addEventListener(type, function (e) {
                e.preventDefault();
                drop.classList.remove('gp-picker-over');
            });
        });
        drop.addEventListener('drop', function (e) {
            if (e.dataTransfer && e.dataTransfer.files) {
                accept(root, e.dataTransfer.files);
            }
        });
        // 키보드로 label 에 포커스했을 때 Enter/Space 로 열리게
        drop.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                input.click();
            }
        });
    }

    // 제거 버튼은 위임으로 한 번만 등록한다(행이 동적으로 늘어나므로)
    document.addEventListener('click', function (e) {
        var btn = e.target.closest('[data-action="picker-remove"]');
        if (!btn) { return; }
        var root = btn.closest('.gp-picker');
        var li = btn.closest('.gp-picker-item');
        if (!root || !li) { return; }
        li.remove();
        sync(root);
        // 서버의 파일은 지우지 않는다 — 폼을 저장할 때 syncAttachments 가 정리한다.
        // 여기서 즉시 지우면 '취소' 로 폼을 빠져나갔을 때 되돌릴 수 없다.
    });

    function initAll() {
        document.querySelectorAll('[data-picker]').forEach(init);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }
    document.addEventListener('htmx:load', initAll);
})();
