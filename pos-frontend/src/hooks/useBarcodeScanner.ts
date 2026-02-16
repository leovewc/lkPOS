import { onMounted, onUnmounted } from 'vue';

export function useBarcodeScanner(
    onScanSuccess: (barcode: string) => void,
    onEmptyEnter?: () => void // 🌟 新增：专门处理空回车的回调
) {
    let barcodeBuffer = '';
    let lastKeyTime = Date.now();

    const handleKeyDown = (e: KeyboardEvent) => {
        const currentTime = Date.now();

        // 如果两次按键间隔超过 50ms，说明不是扫码枪，清空缓冲区
        if (currentTime - lastKeyTime > 50) {
            barcodeBuffer = '';
        }
        lastKeyTime = currentTime;

        if (e.key === 'Enter') {
            if (barcodeBuffer.length > 5) {
                // 扫码枪快速输入了一串数字后按下了回车
                onScanSuccess(barcodeBuffer);
            } else if (barcodeBuffer.length === 0 && onEmptyEnter) {
                // 🌟 缓冲区是空的，说明是人类直接敲击了键盘的回车键
                onEmptyEnter();
            }
            barcodeBuffer = ''; // 触发后清空
        }
        else if (e.key.length === 1) {
            barcodeBuffer += e.key;
        }
    };

    onMounted(() => window.addEventListener('keydown', handleKeyDown));
    onUnmounted(() => window.removeEventListener('keydown', handleKeyDown));
}