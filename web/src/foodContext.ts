import { readonly, ref } from 'vue'

// The detail page owns validated context; sibling assistants do not fetch it again.
const foodId = ref<number>()
let currentOwner: symbol | undefined
export const currentFoodContextId = readonly(foodId)

export function createFoodContext() {
  const owner = Symbol('food-page')
  return {
    set(id: number) { currentOwner = owner; foodId.value = id },
    clear() { if (currentOwner === owner) { currentOwner = undefined; foodId.value = undefined } },
  }
}
