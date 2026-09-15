export interface LandmarkArtwork {
  image: string
  position?: string
}

export const defaultLandmark: LandmarkArtwork = {
  image: '/art/landmarks/lungmen.png',
  position: 'right center',
}

// Artwork is decorative: never replace the dish's real province/city with its name.
export const provinceLandmarks: Record<string, LandmarkArtwork> = {
  北京: { image: '/art/landmarks/beijing.webp' },
  天津: { image: '/art/landmarks/tianjin.webp' },
  河北: { image: '/art/landmarks/hebei.webp' },
  山西: { image: '/art/landmarks/shanxi.webp' },
  内蒙古: { image: '/art/landmarks/inner-mongolia.webp' },
  辽宁: { image: '/art/landmarks/liaoning.webp' },
  吉林: { image: '/art/landmarks/jilin.webp' },
  黑龙江: { image: '/art/landmarks/heilongjiang.webp' },
  上海: { image: '/art/landmarks/shanghai.webp' },
  江苏: { image: '/art/landmarks/jiangsu.webp' },
  浙江: { image: '/art/landmarks/zhejiang.webp' },
  安徽: { image: '/art/landmarks/anhui.webp' },
  福建: { image: '/art/landmarks/fujian.webp' },
  江西: { image: '/art/landmarks/jiangxi.webp' },
  山东: { image: '/art/landmarks/shandong.webp' },
  河南: { image: '/art/landmarks/henan.webp' },
  湖北: { image: '/art/landmarks/hubei.webp' },
  湖南: { image: '/art/landmarks/hunan.webp' },
  广东: { image: '/art/landmarks/guangdong.webp' },
  广西: { image: '/art/landmarks/guangxi.webp' },
  海南: { image: '/art/landmarks/hainan.webp' },
  重庆: { image: '/art/landmarks/chongqing.webp' },
  四川: { image: '/art/landmarks/sichuan.webp' },
  贵州: { image: '/art/landmarks/guizhou.webp' },
  云南: { image: '/art/landmarks/yunnan.webp' },
  西藏: { image: '/art/landmarks/tibet.webp' },
  陕西: { image: '/art/landmarks/shaanxi.webp' },
  甘肃: { image: '/art/landmarks/gansu.webp' },
  青海: { image: '/art/landmarks/qinghai.webp' },
  宁夏: { image: '/art/landmarks/ningxia.webp' },
  新疆: { image: '/art/landmarks/xinjiang.webp' },
  台湾: { image: '/art/landmarks/taiwan.webp' },
  香港: { image: '/art/landmarks/hong-kong.webp' },
  澳门: { image: '/art/landmarks/macau.webp' },
}

function normalizeProvince(name: string) {
  return name.replace(/(?:壮族自治区|回族自治区|维吾尔自治区|特别行政区|自治区|省|市)$/, '')
}

export function resolveLandmark(province?: string): LandmarkArtwork {
  const name = province?.trim() || ''
  return provinceLandmarks[name] ?? provinceLandmarks[normalizeProvince(name)] ?? defaultLandmark
}
