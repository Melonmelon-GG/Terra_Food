export interface LandmarkArtwork {
  image: string
  position?: string
}

export const defaultLandmark: LandmarkArtwork = {
  image: '/art/landmarks/lungmen.png',
  position: 'right center',
}

// Artwork is decorative: never replace the dish's real province/city with its name.
// Add delivered artwork here, e.g. 江苏: { image: '/art/landmarks/zijinshan.webp' }.
export const provinceLandmarks: Record<string, LandmarkArtwork> = {}

export function resolveLandmark(province?: string): LandmarkArtwork {
  const name = province?.trim() || ''
  return provinceLandmarks[name] ?? provinceLandmarks[name.replace(/省$|市$/, '')] ?? defaultLandmark
}
