# 背景音乐

把多个音频文件（推荐 mp3）放在此目录，并在 `music-manifest.json` 中登记曲名、创作艺人和文件路径：

```json
[
  { "name": "音乐一", "artist": "创作艺人一", "src": "/audio/music-1.mp3" },
  { "name": "音乐二", "artist": "创作艺人二", "src": "/audio/music-2.mp3" }
]
```

字段说明：

- `name`：乐曲名称
- `artist`：创作艺人/作者名称
- `src`：相对于 `public` 目录的访问路径
