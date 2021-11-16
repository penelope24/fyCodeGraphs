class Solution {
    public int search(int[] nums, int target) {
        if (nums.length == 1) {
            return nums[0] == target ? 0 : -1;
        }
        int low = 0;
        int high = nums.length-1;
        while (low < high) {
            int cursor = low + (high-low) / 2;
            if (nums[cursor] < nums[high]) {
                high = cursor;
            }
            else {
                low = cursor + 1;
            }
            int r = findTarget(nums, target, low, high);
            if (r != -1) {
                return r;
            }
        }
        return -1;
    }

    public int findTarget(int[] nums ,int target, int low, int high) {
        System.out.println(low);
        System.out.println(high);
        for (int i=low; i<=high; i++) {
            System.out.println(nums[i]);
            if (nums[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] nums = new int[] {1,3};
        int target = 1;
        Solution solution = new Solution();
        int r = solution.search(nums, target);
        System.out.println(r);
    }
}