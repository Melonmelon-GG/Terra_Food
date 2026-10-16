import { createSharedRead } from './sharedRead'

export type MusicTrack = { name: string; artist: string; src: string }
const manifest = createSharedRead<MusicTrack[]>(5 * 60_000, 1)

export function getMusicTracks(signal?: AbortSignal) {
  return manifest.read('music', async requestSignal => {
    const response = await fetch('/audio/music-manifest.json', { signal: requestSignal })
    if (!response.ok) throw new Error('Music manifest unavailable')
    const data: unknown = await response.json()
    if (!Array.isArray(data)) throw new Error('Invalid music manifest')
    return data.filter((track): track is MusicTrack => typeof track?.name === 'string' && typeof track?.src === 'string')
      .map(track => ({ name: track.name, src: track.src, artist: typeof track.artist === 'string' ? track.artist : '' }))
  }, signal)
}
