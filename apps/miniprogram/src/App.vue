<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { restore } from './session';
onShow(() => {
  void restore().catch(() => undefined);
});
</script>
<style>
@import './styles/theme.css';
</style>
