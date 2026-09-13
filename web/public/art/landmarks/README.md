# Region landmark artwork

lungmen.png is an AI-generated Lungmen-inspired placeholder, not an official game asset.
It was generated with the built-in image tool for this redesign.

Prompt: pale sepia Lungmen mountain city, pagoda tower and elevated bridges on the right,
fading into a clean ivory background on the left; no text, UI, food or characters.

Artwork selection lives in web/src/landmarks.ts. Place delivered artwork in this directory
and add a province key to provinceLandmarks, for example:

    江苏: { image: '/art/landmarks/zijinshan.webp', position: 'right center' },
    北京: { image: '/art/landmarks/tiantan.webp', position: 'right center' },

Do not add these entries until their files exist. Province names with a trailing 省 or 市
are normalized for lookup. Unknown regions and failed optional images fall back to Lungmen.
Decorative artwork must never change a dish's actual province, city, name or other data.
The backdrop component is decorative and hidden from screen readers.
The share ticket embeds the selected image before PNG export.
