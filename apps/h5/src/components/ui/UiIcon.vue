<script setup lang="ts">
export type UiIconName =
  | 'home'
  | 'trends'
  | 'archive'
  | 'user'
  | 'body'
  | 'meal'
  | 'workout'
  | 'photo'
  | 'calendar'
  | 'left'
  | 'right'
  | 'close'
  | 'flag';
defineProps<{ name: UiIconName }>();
</script>
<template>
  <svg
    class="ui-icon"
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'home'">
      <path d="m3.5 10 8.5-7 8.5 7v10a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1Z" />
      <path d="M9 21v-8h6v8" />
    </template>
    <template v-else-if="name === 'trends'"><path d="M4 4v16h16M7 14l4-4 4 3 5-6" /></template>
    <template v-else-if="name === 'archive'">
      <rect x="5" y="3.5" width="14" height="17" rx="2.5" />
      <path d="M9 4v16M12 8h4M12 12h4M12 16h3" />
    </template>
    <template v-else-if="name === 'user'">
      <circle cx="12" cy="7.5" r="3.5" />
      <path d="M5 21v-2a7 7 0 0 1 14 0v2" />
    </template>
    <template v-else-if="name === 'body'">
      <rect x="4" y="4" width="16" height="16" rx="4" />
      <path d="M8 10a4.2 4.2 0 0 1 8 0M12 8v5" />
    </template>
    <template v-else-if="name === 'meal'">
      <path d="M5 3v5a2 2 0 0 0 4 0V3M7 3v18M17 3c-3 3-3 7 0 8h2M19 3v18" />
    </template>
    <template v-else-if="name === 'workout'">
      <path d="M3 9v6M6 7v10M18 7v10M21 9v6M6 12h12" />
    </template>
    <template v-else-if="name === 'photo'">
      <path
        d="M8 5 9.5 3h5L16 5h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z"
      />
      <circle cx="12" cy="12" r="4" />
    </template>
    <template v-else-if="name === 'calendar'">
      <rect x="3.5" y="5" width="17" height="16" rx="3" />
      <path d="M7.5 3v4M16.5 3v4M4 10h16M8 14h.01M12 14h.01M16 14h.01M8 17h.01M12 17h.01" />
    </template>
    <path v-else-if="name === 'left'" d="m14 5-7 7 7 7" />
    <path v-else-if="name === 'right'" d="m10 5 7 7-7 7" />
    <template v-else-if="name === 'close'"><path d="m6 6 12 12M18 6 6 18" /></template>
    <path v-else d="M5 21V4.5M5 5h12l-2 4 2 4H5" />
  </svg>
</template>
<style scoped>
.ui-icon {
  flex: none;
  display: block;
}
</style>
